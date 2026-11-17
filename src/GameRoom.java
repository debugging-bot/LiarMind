// GameRoom.java (게임 진행 상태) - 3주차 새로 작성
//  - 스레드가 아니라, 여러 UserService 스레드와 타이머 스레드가 함께 쓰는 "공유 객체"다.
//    그래서 상태를 읽고 바꾸는 public 메소드는 모두 synchronized (한 번에 한 스레드만 들어옴).
//  - 네트워크를 모른다: 소켓이나 UserService 대신 "이름"으로 사람을 구분하고,
//    보낼 메시지는 직접 보내지 않고 Outbox(보낼 목록)에 담아서 돌려준다.
//    실제 전송은 서버가 GameRoom의 잠금을 푼 뒤에 한다.
//    (1주차 규칙: 잠금을 쥔 채로 다른 사람의 잠금을 잡거나 네트워크로 보내지 않는다)

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GameRoom {

    // 게임 단계
    public enum Phase { WAITING, DRAWING, VOTING, GUESSING }

    public static final int MIN_PLAYERS = 3;    // 라이어 1명 + 시민 2명 이상
    public static final int MAX_ROUNDS = 3;     // 바퀴 수는 1~3
    public static final int TURN_SECONDS = 20;  // 한 사람이 그리는 시간
    public static final int VOTE_SECONDS = 30;  // 투표 시간
    public static final int GUESS_SECONDS = 20; // 라이어 정답 시간

    // 보낼 메시지 하나: to가 null이면 모두에게, 아니면 그 이름의 사람에게만
    public static class Out {
        public final String to;
        public final String msg;
        Out(String to, String msg) { this.to = to; this.msg = msg; }
    }

    // GameRoom 메소드가 돌려주는 결과: 보낼 메시지 목록 + 타이머 요청 + 게임이 끝났는지
    public static class Outbox {
        public final List<Out> list = new ArrayList<>();
        public int timerSeconds = 0;   // 0보다 크면 서버가 이 시간 뒤에 onTimeout(timerToken)을 부른다
        public int timerToken = 0;
        public boolean gameEnded = false;

        void all(String msg) { list.add(new Out(null, msg)); }
        void one(String to, String msg) { list.add(new Out(to, msg)); }
    }

    private final WordBank words;
    private final Random random = new Random();

    private Phase phase = Phase.WAITING;
    private List<String> players = new ArrayList<>();       // 이번 게임 참가자 (그리는 순서 = 접속 순서)
    private final Map<String, Integer> scores = new LinkedHashMap<>(); // 점수 (게임이 끝나도 유지)
    private String liar, category, word;
    private int rounds, round, turnIndex;
    private final Map<String, String> votes = new HashMap<>(); // 투표한 사람 -> 지목한 사람

    // 단계가 바뀔 때마다 1씩 늘린다. 타이머가 울렸을 때 번호가 다르면 이미 지난 단계의 타이머라서 무시
    private int phaseToken = 0;

    public GameRoom(WordBank words) {
        this.words = words;
    }

    public synchronized Phase getPhase() {
        return phase;
    }

    // 지금 이 사람이 그릴 수 있는가 (대기실에서는 누구나, 게임 중에는 그릴 차례인 사람만)
    public synchronized boolean canDraw(String name) {
        if (phase == Phase.WAITING) return true;
        return phase == Phase.DRAWING && name.equals(currentDrawer());
    }

    // ───── 게임 시작: /start 바퀴수 ─────
    // names = 서버가 UserVec 잠금 안에서 복사해 준 접속자 목록 (0번이 방장)
    public synchronized Outbox start(String requester, List<String> names, String body) {
        Outbox box = new Outbox();
        if (phase != Phase.WAITING) { box.one(requester, "/system 이미 게임이 진행 중입니다."); return box; }
        if (names.isEmpty() || !names.get(0).equals(requester)) { box.one(requester, "/system 방장만 게임을 시작할 수 있습니다."); return box; }
        if (names.size() < MIN_PLAYERS) { box.one(requester, "/system " + MIN_PLAYERS + "명 이상이어야 시작할 수 있습니다."); return box; }
        int r;
        try {
            r = Integer.parseInt(body.trim());
        } catch (NumberFormatException e) {
            r = 0;
        }
        if (r < 1 || r > MAX_ROUNDS) { box.one(requester, "/system 바퀴 수는 1~" + MAX_ROUNDS + " 사이로 정해 주세요."); return box; }

        players = new ArrayList<>(names);
        for (String p : players) scores.putIfAbsent(p, 0);
        rounds = r;
        round = 1;
        turnIndex = 0;
        votes.clear();

        String[] cw = words.pick(random);
        category = cw[0];
        word = cw[1];
        liar = players.get(random.nextInt(players.size()));

        box.all("/clear"); // 대기실 낙서를 지우고 시작 (2주차 남은 문제)
        box.all("/system 게임을 시작합니다. (" + rounds + "바퀴, " + players.size() + "명)");
        for (String p : players) {
            if (p.equals(liar)) box.one(p, "/word " + category + " ?");  // 라이어는 카테고리만
            else box.one(p, "/word " + category + " " + word);
        }
        startTurn(box);
        return box;
    }

    // ───── 다음 차례: /next (그리는 사람이 직접 누름) ─────
    public synchronized Outbox next(String requester) {
        Outbox box = new Outbox();
        if (phase != Phase.DRAWING || !requester.equals(currentDrawer())) {
            box.one(requester, "/system 지금은 차례를 넘길 수 없습니다.");
            return box;
        }
        advanceTurn(box);
        return box;
    }

    // ───── 투표: /vote 이름 ─────
    public synchronized Outbox vote(String voter, String body) {
        Outbox box = new Outbox();
        String target = body.trim();
        if (phase != Phase.VOTING) { box.one(voter, "/system 지금은 투표 시간이 아닙니다."); return box; }
        if (!players.contains(voter)) { box.one(voter, "/system 이번 게임 참가자만 투표할 수 있습니다."); return box; }
        if (!players.contains(target)) { box.one(voter, "/system 없는 참가자입니다: " + target); return box; }
        if (target.equals(voter)) { box.one(voter, "/system 자기 자신에게는 투표할 수 없습니다."); return box; }
        if (votes.containsKey(voter)) { box.one(voter, "/system 이미 투표했습니다."); return box; }
        votes.put(voter, target);
        box.all("/system " + voter + "님이 투표했습니다. (" + votes.size() + "/" + players.size() + ")");
        if (votes.size() == players.size()) finishVote(box);
        return box;
    }

    // ───── 라이어 정답: /guess 단어 ─────
    public synchronized Outbox guess(String guesser, String body) {
        Outbox box = new Outbox();
        if (phase != Phase.GUESSING || !guesser.equals(liar)) {
            box.one(guesser, "/system 지금은 정답을 말할 수 없습니다.");
            return box;
        }
        String g = body.replace(" ", "").trim();
        box.all("/system 라이어 " + liar + "의 답: " + body.trim());
        if (g.equalsIgnoreCase(word.replace(" ", ""))) endGame(box, "LIAR");
        else endGame(box, "CITIZEN");
        return box;
    }

    // ───── 타이머가 울림 (타이머 스레드가 부름) ─────
    public synchronized Outbox onTimeout(int token) {
        Outbox box = new Outbox();
        if (token != phaseToken) return box; // 이미 다음 단계로 넘어간 뒤의 타이머: 무시
        switch (phase) {
            case DRAWING:
                box.all("/system " + currentDrawer() + "님의 시간이 끝났습니다.");
                advanceTurn(box);
                break;
            case VOTING:
                box.all("/system 투표 시간이 끝났습니다.");
                finishVote(box);
                break;
            case GUESSING:
                box.all("/system 라이어가 시간 안에 답하지 못했습니다.");
                endGame(box, "CITIZEN");
                break;
            default:
                break;
        }
        return box;
    }

    // ───── 게임 중에 누가 나감 (logout에서 부름) ─────
    public synchronized Outbox playerLeft(String name) {
        Outbox box = new Outbox();
        scores.remove(name);
        if (phase == Phase.WAITING || !players.contains(name)) return box;

        boolean wasDrawer = phase == Phase.DRAWING && name.equals(currentDrawer());
        int idx = players.indexOf(name);
        players.remove(idx);
        if (idx < turnIndex) turnIndex--; // 앞사람이 빠지면 지금 차례 번호가 한 칸 당겨짐

        if (name.equals(liar) || players.size() < MIN_PLAYERS) {
            box.all("/system " + (name.equals(liar) ? "라이어가 나가서" : "인원이 부족해서") + " 게임을 중단합니다.");
            endGame(box, "CANCEL");
            return box;
        }
        if (phase == Phase.DRAWING && wasDrawer) {
            if (turnIndex >= players.size()) { turnIndex = 0; round++; } // 마지막 사람이 나간 경우
            if (round > rounds) startVote(box);
            else startTurn(box);
        } else if (phase == Phase.VOTING) {
            votes.remove(name);
            votes.values().removeIf(t -> t.equals(name)); // 나간 사람을 지목한 표는 무효
            if (votes.size() == players.size()) finishVote(box);
        }
        return box;
    }

    // ─────────── 아래는 내부 동작 (이미 잠금 안에서만 불림) ───────────

    private String currentDrawer() {
        return players.get(turnIndex);
    }

    private void startTurn(Outbox box) {
        phase = Phase.DRAWING;
        phaseToken++;
        box.all("/turn " + currentDrawer() + " " + round + " " + TURN_SECONDS);
        box.timerSeconds = TURN_SECONDS;
        box.timerToken = phaseToken;
    }

    private void advanceTurn(Outbox box) {
        turnIndex++;
        if (turnIndex >= players.size()) { // 한 바퀴 끝
            turnIndex = 0;
            round++;
        }
        if (round > rounds) startVote(box);
        else startTurn(box);
    }

    private void startVote(Outbox box) {
        phase = Phase.VOTING;
        phaseToken++;
        votes.clear();
        box.all("/votestart");
        box.all("/system 라이어라고 생각하는 사람에게 투표하세요. (" + VOTE_SECONDS + "초)");
        box.timerSeconds = VOTE_SECONDS;
        box.timerToken = phaseToken;
    }

    // 표를 세서 가장 많이 받은 사람을 찾는다. 동점이거나 아무도 투표하지 않으면 라이어를 못 찾은 것
    private void finishVote(Outbox box) {
        Map<String, Integer> count = new LinkedHashMap<>();
        for (String t : votes.values()) count.merge(t, 1, Integer::sum);
        String top = null;
        int max = 0;
        boolean tie = false;
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            if (e.getValue() > max) { top = e.getKey(); max = e.getValue(); tie = false; }
            else if (e.getValue() == max) tie = true;
        }
        StringBuilder sb = new StringBuilder("/system 투표 결과:");
        if (count.isEmpty()) sb.append(" 투표 없음");
        for (Map.Entry<String, Integer> e : count.entrySet()) sb.append(" ").append(e.getKey()).append(" ").append(e.getValue()).append("표");
        box.all(sb.toString());

        if (top == null || tie) {
            box.all("/system 한 사람을 정하지 못했습니다. 라이어를 찾지 못했습니다.");
            endGame(box, "LIAR");
        } else if (!top.equals(liar)) {
            box.all("/system " + top + "님은 라이어가 아닙니다.");
            endGame(box, "LIAR");
        } else {
            phase = Phase.GUESSING;
            phaseToken++;
            box.all("/guessstart " + liar);
            box.all("/system " + liar + "님이 라이어였습니다! 제시어를 맞히면 라이어가 이깁니다. (" + GUESS_SECONDS + "초)");
            box.timerSeconds = GUESS_SECONDS;
            box.timerToken = phaseToken;
        }
    }

    // 결과 전송, 점수 반영, 대기실로 돌아감
    private void endGame(Outbox box, String winner) {
        if (winner.equals("CITIZEN")) {
            for (String p : players) if (!p.equals(liar)) scores.merge(p, 1, Integer::sum);
        } else if (winner.equals("LIAR")) {
            if (players.contains(liar)) scores.merge(liar, 2, Integer::sum);
        }
        box.all("/result " + winner + " " + liar + " " + word);
        box.all("/score " + scoreList());
        phase = Phase.WAITING;
        phaseToken++;  // 남아 있는 타이머를 모두 무효로
        votes.clear();
        box.gameEnded = true;
    }

    private String scoreList() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(e.getKey()).append(":").append(e.getValue());
        }
        return sb.toString();
    }
}
