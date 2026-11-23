// ProfilePanel.java (프로필 칸 하나: 캐릭터, 방장 표시, 닉네임, 점수) - 새로 작성
// WindowBuilder의 Design 탭에서 게임 화면에 보이고, 이 파일 자체도 Design 탭으로 열어 배치를 바꿀 수 있게 별도 파일로 분리
//  - 3주차에 추가한 점: 점수 표시(setScore), 그리는 사람 강조(setTurn)
//  - 4주차에 추가한 점: 캐릭터 그림(setAvatar, 80×80, 파일이 없으면 닉네임 첫 글자), 준비 표시(setReady)

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;

public class ProfilePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    // 닉네임마다 아바타 색을 다르게 (캐릭터 그림이 없을 때 대신 표시)
    private static final Color[] AVATAR_COLORS = {
        new Color(244, 143, 177), new Color(129, 199, 132), new Color(100, 181, 246),
        new Color(255, 183, 77), new Color(186, 104, 200), new Color(77, 208, 225),
        new Color(255, 138, 101), new Color(161, 136, 127)
    };

    // 캐릭터 그림은 한 번만 읽어서 여러 곳이 같이 쓴다 (경로 → 그림, 파일이 없으면 null)
    private static final Map<String, ImageIcon> ICON_CACHE = new HashMap<>();

    // "images/avatar/avatar3.png"처럼 프로젝트 폴더 기준 경로의 그림을 읽는다 (folder: "avatar" 80×80, "avatar_big" 160×160)
    // 파일이 없으면(GitHub에서 새로 받은 컴퓨터 등) null → 부르는 쪽이 글자로 대신 표시
    public static ImageIcon loadIcon(String folder, int number) {
        if (number < 1) return null;
        String path = "images/" + folder + "/avatar" + number + ".png";
        if (!ICON_CACHE.containsKey(path)) {
            ImageIcon icon = new ImageIcon(path);
            ICON_CACHE.put(path, icon.getIconWidth() > 0 ? icon : null); // 너비가 0 이하이면 파일을 못 찾은 것
        }
        return ICON_CACHE.get(path);
    }

    private JLabel lblAvatar;
    private JLabel lblHost;
    private JLabel lblName;
    private JLabel lblScore;

    // 테두리와 윗줄 글자를 다시 그릴 때 필요한 상태
    private boolean host = false;
    private boolean me = false;
    private boolean turn = false;
    private boolean ready = false;

    /**
     * Create the panel.
     */
    public ProfilePanel() {
        setPreferredSize(new Dimension(220, 100));
        setLayout(null);

        lblAvatar = new JLabel("");
        lblAvatar.setHorizontalAlignment(SwingConstants.CENTER);
        lblAvatar.setFont(new Font("맑은 고딕", Font.BOLD, 30));
        lblAvatar.setForeground(Color.WHITE);
        lblAvatar.setOpaque(true);
        lblAvatar.setBounds(10, 10, 80, 80); // 팀원이 준 80×80 그림을 늘리거나 줄이지 않고 그대로 쓰는 크기
        add(lblAvatar);

        lblHost = new JLabel("");
        lblHost.setFont(new Font("맑은 고딕", Font.BOLD, 11));
        lblHost.setForeground(new Color(230, 140, 0));
        lblHost.setBounds(98, 12, 118, 20);
        add(lblHost);

        lblName = new JLabel("");
        lblName.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        lblName.setBounds(98, 34, 118, 28);
        add(lblName);

        lblScore = new JLabel("");
        lblScore.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        lblScore.setBounds(98, 62, 118, 22);
        add(lblScore);

        clear();
    }

    // 접속자 표시 (캐릭터·점수·강조·준비는 게임 화면이 이어서 set...으로 채운다)
    public void setUser(String name, boolean host, boolean me) {
        this.host = host;
        this.me = me;
        this.turn = false;
        this.ready = false;
        lblAvatar.setIcon(null);
        lblAvatar.setText(name.substring(0, 1)); // 닉네임 첫 글자
        lblAvatar.setBackground(AVATAR_COLORS[Math.floorMod(name.hashCode(), AVATAR_COLORS.length)]);
        lblName.setText(me ? name + " (나)" : name);
        lblName.setForeground(Color.BLACK);
        lblScore.setText("0점");
        setBackground(me ? new Color(255, 250, 230) : Color.WHITE);
        updateLook();
    }

    // 캐릭터 번호(1~8)의 그림을 표시. 번호가 없거나(0) 파일이 없으면 닉네임 첫 글자를 그대로 둔다
    public void setAvatar(int number) {
        ImageIcon icon = loadIcon("avatar", number);
        if (icon == null) return;
        lblAvatar.setText("");
        lblAvatar.setIcon(icon);
        lblAvatar.setBackground(Color.WHITE);
    }

    // 점수 표시 (/score를 받았을 때, 또 /users로 칸을 다시 채울 때 게임 화면이 불러 준다)
    public void setScore(int score) {
        lblScore.setText(score + "점");
    }

    // 지금 그리는 사람이면 파란 굵은 테두리와 "▶ 그리는 중" 표시
    public void setTurn(boolean turn) {
        this.turn = turn;
        updateLook();
    }

    // 대기실에서 준비한 사람이면 "● 준비 완료" 표시 (방장은 준비하지 않음)
    public void setReady(boolean ready) {
        this.ready = ready;
        updateLook();
    }

    private void updateLook() {
        String top = host ? "★ 방장" : (ready ? "● 준비 완료" : "");
        if (turn) top = top.isEmpty() ? "▶ 그리는 중" : top + " ▶ 그리는 중";
        lblHost.setText(top);
        lblHost.setForeground(!host && ready && !turn ? new Color(40, 150, 60) : new Color(230, 140, 0));
        if (turn) setBorder(new LineBorder(new Color(30, 120, 220), 4, true));
        else setBorder(new LineBorder(me ? new Color(240, 180, 60) : new Color(190, 190, 190), 2, true));
    }

    // 빈 자리 표시
    public void clear() {
        host = false;
        me = false;
        turn = false;
        ready = false;
        lblAvatar.setIcon(null);
        lblAvatar.setText("");
        lblAvatar.setBackground(new Color(225, 225, 225));
        lblHost.setText("");
        lblName.setText("빈 자리");
        lblName.setForeground(Color.GRAY);
        lblScore.setText("");
        setBackground(new Color(245, 245, 245));
        setBorder(new LineBorder(new Color(220, 220, 220), 2, true));
    }
}
