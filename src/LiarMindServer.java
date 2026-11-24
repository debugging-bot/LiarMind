// LiarMindServer.java (라이어마인드 서버)
// 수업 예제 JavaChatServer.java를 바탕으로 확장
//  - 수업 예제와 같은 점: 서버 관리자 GUI, ServerSocket, AcceptServer 스레드, 클라이언트별 UserService 스레드,
//                       Vector에 접속자 저장, DataInputStream/DataOutputStream의 readUTF()/writeUTF()
//  - 1주차에 추가한 점: 로그인 검사(빈 이름, 공백, 중복, 최대 8명, 게임 시작 후 접속 거부),
//                       접속자 목록(/users) 전송, 방장(첫 번째 접속자) 관리, 명령어 switch 처리,
//                       /chat, /exit 처리, 접속 끊김 시 안전한 정리, 스레드 동기화
//  - 2주차에 추가한 점: /draw, /clear 중계와 그림 메시지 검사
//  - 3주차에 추가한 점: 게임 진행(GameRoom), 제시어 목록(WordBank, words.txt), 차례 타이머(java.util.Timer),
//                       /start, /next, /vote, /guess 처리, 차례인 사람만 그리기, 게임 중 퇴장 처리
//  - 4주차에 추가한 점: 게임 중 전체 지우기 금지, 준비(/ready), 캐릭터 번호 배정(/avatars),
//                       그리는 순서 무작위·동점자 다시 투표 (GameRoom)

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class LiarMindServer extends JFrame {
    private static final long serialVersionUID = 1L;

    public static final int MAX_USERS = 8;          // 프로필 칸이 좌우 4칸씩이므로 최대 8명
    public static final int MAX_NAME_LENGTH = 10;   // 프로필 칸에 들어갈 수 있는 닉네임 길이
    public static final int AVATAR_COUNT = 8;       // 캐릭터 그림 수 (images/avatar/avatar1.png ~ avatar8.png)
    private final Random random = new Random();

    private JPanel contentPane;
    private JTextArea textArea;
    private JTextField txtPortNumber;
    private JButton btnServerStart;

    private ServerSocket socket; // 서버소켓

    // 로그인까지 끝난 사용자만 저장 (Vector 순서 = 접속 순서 = 프로필 칸 순서, 0번이 방장)
    private Vector<UserService> UserVec = new Vector<>();

    // 게임이 시작되면 true. true이면 새 접속을 거부
    // 바꾸는 곳: /start (UserVec 잠금 안에서 true), 게임이 끝날 때 (false)
    private volatile boolean gameStarted = false;

    private final WordBank words = new WordBank("words.txt"); // 제시어 목록 (프로젝트 폴더의 words.txt)
    // 게임 메시지 줄: GameRoom이 넣고, 보내는 스레드(GameSender) 하나가 넣은 순서대로 꺼내 보낸다
    private final BlockingQueue<GameRoom.Outbox> outQueue = new LinkedBlockingQueue<>();
    private final GameRoom game = new GameRoom(words, outQueue); // 게임 진행 상태 (스레드가 아닌 공유 객체)
    private final Timer timer = new Timer(true);               // 차례·투표 시간을 재는 스레드 1개 (데몬)

    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    LiarMindServer frame = new LiarMindServer();
                    frame.setVisible(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public LiarMindServer() {
        setTitle("LiarMind Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 420, 460);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);
        contentPane.setLayout(null);

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBounds(12, 10, 380, 318);
        contentPane.add(scrollPane);

        textArea = new JTextArea();
        textArea.setEditable(false);
        scrollPane.setViewportView(textArea);

        JLabel lblNewLabel = new JLabel("Port Number");
        lblNewLabel.setBounds(12, 338, 87, 26);
        contentPane.add(lblNewLabel);

        txtPortNumber = new JTextField();
        txtPortNumber.setHorizontalAlignment(SwingConstants.CENTER);
        txtPortNumber.setText("30000");
        txtPortNumber.setBounds(111, 338, 281, 26);
        contentPane.add(txtPortNumber);
        txtPortNumber.setColumns(10);

        btnServerStart = new JButton("Server Start");
        btnServerStart.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    socket = new ServerSocket(Integer.parseInt(txtPortNumber.getText().trim()));
                } catch (NumberFormatException | IOException e1) {
                    // 수업 예제는 실패해도 계속 진행했지만, 여기서는 실패하면 멈추고 다시 시도할 수 있게 함
                    AppendText("서버 시작 실패: " + e1.getMessage());
                    return;
                }
                AppendText("LiarMind Server Running.. (port " + txtPortNumber.getText().trim() + ")");
                AppendText("제시어: " + words.summary());
                btnServerStart.setText("LiarMind Server Running..");
                btnServerStart.setEnabled(false); // 서버를 더이상 실행시키지 못 하게 막는다
                txtPortNumber.setEnabled(false);  // 더이상 포트번호 수정못 하게 막는다
                AcceptServer accept_server = new AcceptServer();
                accept_server.start();
                GameSender sender = new GameSender();
                sender.setDaemon(true); // 서버 창을 닫으면 같이 끝남
                sender.start();
            }
        });
        btnServerStart.setBounds(12, 374, 380, 35);
        contentPane.add(btnServerStart);

        // 서버 창을 닫을 때 서버소켓을 닫아서 accept()에서 대기 중인 AcceptServer를 깨워 종료
        // (수업 예제 주석에서 소개된 WindowAdapter 방식)
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeServerSocket();
            }
        });
    }

    private void closeServerSocket() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            // 종료 중이므로 무시
        }
    }

    // 게임 메시지를 보내는 스레드 (하나뿐). 줄에서 하나씩 꺼내 넣은 순서대로 보낸다
    // 여러 스레드가 각자 보내면 "(2/4)" 다음에 "(1/4)"가 도착하는 것처럼 순서가 뒤바뀔 수 있어서 4주차에 추가
    class GameSender extends Thread {
        public void run() {
            while (true) {
                try {
                    send(outQueue.take()); // 줄이 비어 있으면 들어올 때까지 기다린다
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    // 새로운 참가자 accept() 하고 user thread를 새로 생성한다.
    class AcceptServer extends Thread {
        public void run() {
            while (!socket.isClosed()) {
                try {
                    AppendText("Waiting clients ...");
                    Socket client_socket = socket.accept(); // accept가 일어나기 전까지는 무한 대기중
                    client_socket.setTcpNoDelay(true);      // 작은 메시지(그림 좌표)를 모으지 않고 바로 보냄
                    AppendText("새로운 연결 from " + client_socket.getRemoteSocketAddress());
                    // 로그인 검사는 UserService 스레드 안에서 함
                    // (수업 예제처럼 생성자에서 readUTF()를 하면 그동안 다른 사람의 accept()가 멈추기 때문)
                    UserService new_user = new UserService(client_socket);
                    new_user.start();
                } catch (IOException e) {
                    if (socket.isClosed()) {
                        AppendText("서버소켓이 닫혀서 접속 대기를 종료합니다.");
                        break;
                    }
                    AppendText("!!!! accept 에러 발생... !!!!");
                }
            }
        }
    }

    // JTextArea에 문자열 출력
    // 여러 UserService 스레드가 동시에 부르므로, 화면 변경은 EDT에게 맡긴다
    public void AppendText(String str) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                textArea.append(str + "\n");
                textArea.setCaretPosition(textArea.getText().length());
            }
        });
    }

    // 접속자 이름을 "이름1,이름2,이름3" 형태로 만든다 (0번이 방장)
    private String makeUserList() {
        StringBuilder sb = new StringBuilder();
        synchronized (UserVec) {
            for (int i = 0; i < UserVec.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(UserVec.get(i).UserName);
            }
        }
        return sb.toString();
    }

    // 로그인한 모든 사용자에게 전송
    public void WriteAll(String msg) {
        // 목록을 복사해서 보낸다: 보내는 도중 누가 나가서 Vector가 바뀌어도 안전
        Vector<UserService> copy;
        synchronized (UserVec) {
            copy = new Vector<>(UserVec);
        }
        for (UserService user : copy) {
            user.WriteOne(msg);
        }
    }

    // 접속자마다 캐릭터 번호를 "이름1:번호,이름2:번호" 형태로 (순서는 /users와 같음)
    private String makeAvatarList() {
        StringBuilder sb = new StringBuilder();
        synchronized (UserVec) {
            for (int i = 0; i < UserVec.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(UserVec.get(i).UserName).append(":").append(UserVec.get(i).avatar);
            }
        }
        return sb.toString();
    }

    // 아직 아무도 쓰지 않는 캐릭터 번호(1~8) 중 하나를 무작위로 고른다 (UserVec 잠금 안에서만 부른다)
    private int pickAvatar() {
        List<Integer> free = new ArrayList<>();
        for (int n = 1; n <= AVATAR_COUNT; n++) free.add(n);
        for (UserService u : UserVec) free.remove(Integer.valueOf(u.avatar));
        return free.get(random.nextInt(free.size())); // 최대 8명이므로 항상 하나 이상 남아 있다
    }

    // 접속자 목록이 바뀔 때마다 모두에게 /users, /avatars, /ready 전송
    public void sendUserList() {
        String list = makeUserList();
        int count = list.isEmpty() ? 0 : list.split(",").length; // 보낸 목록 기준으로 세야 로그와 목록이 일치
        WriteAll("/users " + list);
        WriteAll("/avatars " + makeAvatarList());
        AppendText("현재 참가자(" + count + "명): " + list);
        synchronized (UserVec) { // 잠금 순서: UserVec -> GameRoom
            game.usersChanged(copyNames()); // 나간 사람, 새 방장을 준비 목록에서 빼고 /ready 다시 전송
        }
    }

    // 접속자 이름 목록 복사 (UserVec 잠금 안에서 부른다, 0번이 방장)
    private List<String> copyNames() {
        List<String> names = new ArrayList<>();
        for (UserService u : UserVec) names.add(u.UserName);
        return names;
    }

    // /draw 뒤의 값 검사: 정수 6개(x1 y1 x2 y2 색RGB 굵기)이고 굵기가 1~50이면 true
    // 잘못된 메시지를 그대로 전달하면 받는 클라이언트들이 숫자 변환 중 오류가 나므로 서버에서 먼저 걸러낸다
    public static final int MAX_STROKE_WIDTH = 50;

    private boolean isValidDraw(String body) {
        String[] v = body.trim().split(" ");
        if (v.length != 6) return false;
        try {
            for (String s : v) Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return false;
        }
        int width = Integer.parseInt(v[5]);
        return width >= 1 && width <= MAX_STROKE_WIDTH;
    }

    // 이름으로 접속자 찾기 (없으면 null)
    private UserService findUser(String name) {
        synchronized (UserVec) {
            for (UserService u : UserVec) {
                if (u.UserName.equals(name)) return u;
            }
        }
        return null;
    }

    // Outbox를 실제로 보낸다 (보내는 스레드 GameSender만 부른다)
    private void send(GameRoom.Outbox box) {
        for (GameRoom.Out o : box.list) {
            if (o.to == null) {
                WriteAll(o.msg);
                AppendText("[게임] " + o.msg);
            } else {
                UserService u = findUser(o.to);
                if (u != null) u.WriteOne(o.msg);
                AppendText("[게임 → " + o.to + "] " + o.msg);
            }
        }
        if (box.gameEnded) gameStarted = false; // 대기실로 돌아왔으므로 다시 입장 가능
        if (box.timerSeconds > 0) {
            final int token = box.timerToken;
            // 정해진 시간 뒤에 타이머 스레드가 run()을 부른다. 그 사이 단계가 바뀌었으면 GameRoom이 무시한다
            timer.schedule(new TimerTask() {
                public void run() {
                    game.onTimeout(token); // 결과는 줄에 들어가고 보내는 스레드가 보낸다
                }
            }, box.timerSeconds * 1000L);
        }
    }

    // 닉네임 검사. 문제가 없으면 null, 문제가 있으면 /reject 뒤에 붙일 이유를 돌려준다
    // 접속 인원 확인과 목록 추가를 한 번에 해야 하므로 UserVec으로 동기화
    private String tryLogin(UserService user, String name) {
        if (name.isEmpty()) return "empty";                    // 이름 없음
        if (name.contains(" ") || name.contains(",")) return "invalid"; // 공백, 쉼표 금지 (메시지 구분자이므로)
        if (name.length() > MAX_NAME_LENGTH) return "toolong"; // 너무 긴 이름
        synchronized (UserVec) {
            if (gameStarted) return "started";               // 게임 진행 중 (/start와 같은 잠금 안에서 확인)
            if (UserVec.size() >= MAX_USERS) return "full";    // 8명 초과
            for (UserService u : UserVec) {
                if (u.UserName.equals(name)) return "duplicate"; // 중복 닉네임
            }
            user.UserName = name;
            user.avatar = pickAvatar(); // 다른 사람과 겹치지 않는 캐릭터 번호
            UserVec.add(user);  // 검사를 통과한 사람만 목록에 추가
        }
        return null;
    }

    // User 당 생성되는 Thread
    class UserService extends Thread {
        private Socket client_socket;
        private DataInputStream dis;
        private DataOutputStream dos;
        private String UserName = "";
        private int avatar = 0; // 캐릭터 번호 1~8 (로그인할 때 정해짐)
        private boolean loggedIn = false;
        private boolean closed = false;

        public UserService(Socket client_socket) {
            this.client_socket = client_socket;
            try {
                dis = new DataInputStream(client_socket.getInputStream());
                dos = new DataOutputStream(client_socket.getOutputStream());
            } catch (IOException e) {
                AppendText("userService error");
                closeSocket();
            }
        }

        // 이 사용자에게만 전송
        // 여러 스레드가 동시에 같은 dos에 쓰면 메시지가 섞일 수 있으므로 synchronized
        // (logout()은 synchronized 블록 밖에서 부른다: 두 사람이 동시에 끊길 때 서로의 잠금을 기다리는 교착 상태 방지)
        public void WriteOne(String msg) {
            boolean failed = false;
            synchronized (this) {
                if (closed) return;
                try {
                    dos.writeUTF(msg);
                } catch (IOException e) {
                    failed = true;
                }
            }
            if (failed) {
                AppendText("dos.write() error (" + UserName + ")");
                logout();
            }
        }

        private void closeSocket() {
            closed = true;
            try {
                if (dis != null) dis.close();
                if (dos != null) dos.close();
                if (client_socket != null) client_socket.close();
            } catch (IOException e) {
                // 이미 닫힌 경우 무시
            }
        }

        // 퇴장 처리. readUTF 오류, writeUTF 오류, /exit 어디서 불려도 한 번만 실행되게 한다
        public void logout() {
            synchronized (this) {
                if (closed) return;
                closeSocket();
            }
            if (!loggedIn) return; // 로그인 전에 끊긴 사람은 목록에 없으므로 알릴 필요 없음

            boolean removed;
            synchronized (UserVec) {
                removed = UserVec.remove(this);
            }
            if (removed) {
                AppendText("[" + UserName + "] 퇴장");
                WriteAll("/system " + UserName + "님이 퇴장했습니다.");
                sendUserList(); // 방장이 나가면 다음 사람이 0번이 되어 자동으로 방장이 됨
                game.playerLeft(UserName); // 게임 중이었다면 차례 넘기기, 투표 다시 세기, 게임 중단 등
            }
        }

        // /start 바퀴수: 방장만. 접속자 목록 복사와 gameStarted = true를 한 번에 해야
        // 시작하는 순간 들어온 사람이 게임에 끼지 못한 채 남는 일이 없다
        // 잠금 순서: UserVec -> GameRoom (반대 순서로 잡는 곳은 없다)
        private void startGame(String body) {
            synchronized (UserVec) {
                game.start(UserName, copyNames(), body); // 보낼 메시지는 줄에 들어가고 보내는 스레드가 보낸다
                if (game.getPhase() != GameRoom.Phase.WAITING) gameStarted = true;
            }
        }

        // 첫 메시지는 반드시 "/login 닉네임"
        private boolean handleLogin() throws IOException {
            String line = dis.readUTF().trim();
            String[] parts = line.split(" ", 2);
            if (!parts[0].equals("/login")) {
                WriteOne("/reject login_required");
                return false;
            }
            String name = parts.length > 1 ? parts[1].trim() : "";
            String reason = tryLogin(this, name);
            if (reason != null) {
                AppendText("접속 거부(" + reason + "): " + name);
                WriteOne("/reject " + reason);
                return false;
            }
            loggedIn = true;
            AppendText("[" + UserName + "] 입장");
            WriteOne("/loginok " + UserName);                     // 본인에게 접속 성공 알림
            WriteAll("/system " + UserName + "님이 입장했습니다."); // 모두에게 입장 알림
            sendUserList();                                         // 모두의 프로필 칸 갱신
            return true;
        }

        public void run() {
            try {
                if (!handleLogin()) {
                    closeSocket();
                    return;
                }
                while (!closed) {
                    String msg = dis.readUTF().trim();

                    // "/명령어 나머지" 로 나눈다. 나머지 안에는 공백이 있어도 됨 (채팅 내용 등)
                    String[] parts = msg.split(" ", 2);
                    String cmd = parts[0];
                    String body = parts.length > 1 ? parts[1] : "";

                    // /draw는 마우스를 움직일 때마다 1초에 수십 개씩 오므로 서버 화면에 찍지 않는다
                    // (전부 찍으면 로그 창이 금방 수만 줄이 되어 서버 화면이 느려짐)
                    if (!cmd.equals("/draw")) AppendText(UserName + " > " + msg);

                    switch (cmd) {
                        case "/chat": // 채팅: 보낸 사람 이름을 붙여서 모두에게
                            if (!body.isEmpty()) WriteAll("/chat " + UserName + " " + body);
                            break;

                        case "/draw": // 선분 하나: "/draw x1 y1 x2 y2 색RGB 굵기"
                            // 형식이 맞는 것만 모두에게 그대로 전달 (보낸 사람 포함: 모든 화면의 그림이 항상 같게)
                            // 게임 중에는 지금 그릴 차례인 사람의 것만 전달 (1초에 수십 개라 안내 메시지 없이 무시)
                            if (!game.canDraw(UserName)) break;
                            if (isValidDraw(body)) {
                                WriteAll("/draw " + body.trim());
                            } else {
                                AppendText(UserName + " > 잘못된 /draw 무시: " + body);
                            }
                            break;

                        case "/clear": // 지우기: 대기실에서는 전체, 게임 중에는 그리는 사람이 자기가 그린 선만
                            if (game.canClear()) WriteAll("/clear");                          // 대기실: 전체 지우기
                            else if (game.canDraw(UserName)) WriteAll("/clear " + UserName);  // 게임 중: 그린 사람 선만
                            else WriteOne("/system 지금은 그림을 지울 수 없습니다.");
                            break;

                        case "/ready": // 준비 / 준비 취소 (방장 제외, 대기실에서만)
                            synchronized (UserVec) { // 잠금 순서: UserVec -> GameRoom
                                game.toggleReady(UserName, copyNames());
                            }
                            break;

                        case "/start": // 게임 시작: "/start 바퀴수" (방장만)
                            startGame(body);
                            break;

                        case "/next": // 다음 차례로 (그리는 사람만)
                            game.next(UserName);
                            break;

                        case "/vote": // 투표: "/vote 이름"
                            game.vote(UserName, body);
                            break;

                        case "/guess": // 라이어의 정답: "/guess 단어"
                            game.guess(UserName, body);
                            break;

                        case "/exit": // 종료 명령
                            logout();
                            return;

                        default:
                            WriteOne("/system 알 수 없는 명령어입니다: " + cmd);
                            break;
                    }
                }
            } catch (IOException e) {
                // 클라이언트 창을 그냥 닫으면 readUTF()에서 예외 발생 -> 정상적인 퇴장으로 처리
                logout();
            }
        }
    }
}