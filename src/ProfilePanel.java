// ProfilePanel.java (프로필 칸 하나: 아바타, 방장 표시, 닉네임, 점수) - 새로 작성
// WindowBuilder의 Design 탭에서 게임 화면에 보이고, 이 파일 자체도 Design 탭으로 열어 배치를 바꿀 수 있게 별도 파일로 분리
//  - 3주차에 추가한 점: 점수 표시(setScore), 그리는 사람 강조(setTurn)

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;

public class ProfilePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    // 닉네임마다 아바타 색을 다르게 (같은 닉네임은 항상 같은 색)
    private static final Color[] AVATAR_COLORS = {
        new Color(244, 143, 177), new Color(129, 199, 132), new Color(100, 181, 246),
        new Color(255, 183, 77), new Color(186, 104, 200), new Color(77, 208, 225),
        new Color(255, 138, 101), new Color(161, 136, 127)
    };

    private JLabel lblAvatar;
    private JLabel lblHost;
    private JLabel lblName;
    private JLabel lblScore;

    // 테두리와 윗줄 글자를 다시 그릴 때 필요한 상태
    private boolean host = false;
    private boolean me = false;
    private boolean turn = false;

    /**
     * Create the panel.
     */
    public ProfilePanel() {
        setPreferredSize(new Dimension(220, 100));
        setLayout(null);

        lblAvatar = new JLabel("");
        lblAvatar.setHorizontalAlignment(SwingConstants.CENTER);
        lblAvatar.setFont(new Font("맑은 고딕", Font.BOLD, 28));
        lblAvatar.setForeground(Color.WHITE);
        lblAvatar.setOpaque(true);
        lblAvatar.setBounds(12, 15, 70, 70);
        add(lblAvatar);

        lblHost = new JLabel("");
        lblHost.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        lblHost.setForeground(new Color(230, 140, 0));
        lblHost.setBounds(92, 12, 124, 20);
        add(lblHost);

        lblName = new JLabel("");
        lblName.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        lblName.setBounds(92, 34, 124, 28);
        add(lblName);

        lblScore = new JLabel("");
        lblScore.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        lblScore.setBounds(92, 62, 120, 22);
        add(lblScore);

        clear();
    }

    // 접속자 표시
    public void setUser(String name, boolean host, boolean me) {
        this.host = host;
        this.me = me;
        this.turn = false;
        lblAvatar.setText(name.substring(0, 1)); // 닉네임 첫 글자
        lblAvatar.setBackground(AVATAR_COLORS[Math.floorMod(name.hashCode(), AVATAR_COLORS.length)]);
        lblName.setText(me ? name + " (나)" : name);
        lblName.setForeground(Color.BLACK);
        lblScore.setText("0점");
        setBackground(me ? new Color(255, 250, 230) : Color.WHITE);
        updateLook();
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

    private void updateLook() {
        String top = host ? "★ 방장" : "";
        if (turn) top = top.isEmpty() ? "▶ 그리는 중" : top + "  ▶ 그리는 중";
        lblHost.setText(top);
        if (turn) setBorder(new LineBorder(new Color(30, 120, 220), 4, true));
        else setBorder(new LineBorder(me ? new Color(240, 180, 60) : new Color(190, 190, 190), 2, true));
    }

    // 빈 자리 표시
    public void clear() {
        host = false;
        me = false;
        turn = false;
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
