// LiarMindClientMain.java (라이어마인드 클라이언트 접속 화면)
// 수업 예제 JavaChatClientMain.java를 바탕으로 확장
//  - 수업 예제와 같은 점: 닉네임, IP, 포트 입력 화면과 Connect 버튼, Myaction 내부 클래스
//  - 1주차에 추가한 점: 닉네임 검사(빈 이름, 공백·쉼표, 10자 초과), 포트 번호 검사,
//                       접속 중 버튼 잠금, 접속 실패·거부 시 이유를 띄우고 이 화면으로 돌아오기

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

public class LiarMindClientMain extends JFrame {
    private static final long serialVersionUID = 1L;

    public static final int MAX_NAME_LENGTH = 10; // 서버와 같은 기준 (프로필 칸에 들어갈 길이)

    private JPanel contentPane;
    private JTextField txtUserName;
    private JTextField txtIpAddress;
    private JTextField txtPortNumber;
    private JButton btnConnect;

    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    LiarMindClientMain frame = new LiarMindClientMain();
                    frame.setVisible(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Create the frame.
     */
    public LiarMindClientMain() {
        setTitle("LiarMind 접속");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 254, 321);
        setResizable(false);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);
        contentPane.setLayout(null);

        JLabel lblNewLabel = new JLabel("닉네임");
        lblNewLabel.setBounds(12, 39, 82, 33);
        contentPane.add(lblNewLabel);

        txtUserName = new JTextField();
        txtUserName.setHorizontalAlignment(SwingConstants.CENTER);
        txtUserName.setBounds(101, 39, 116, 33);
        contentPane.add(txtUserName);
        txtUserName.setColumns(10);

        JLabel lblIpAddress = new JLabel("IP Address");
        lblIpAddress.setBounds(12, 100, 82, 33);
        contentPane.add(lblIpAddress);

        txtIpAddress = new JTextField();
        txtIpAddress.setHorizontalAlignment(SwingConstants.CENTER);
        txtIpAddress.setText("127.0.0.1");
        txtIpAddress.setColumns(10);
        txtIpAddress.setBounds(101, 100, 116, 33);
        contentPane.add(txtIpAddress);

        JLabel lblPortNumber = new JLabel("Port Number");
        lblPortNumber.setBounds(12, 163, 82, 33);
        contentPane.add(lblPortNumber);

        txtPortNumber = new JTextField();
        txtPortNumber.setText("30000");
        txtPortNumber.setHorizontalAlignment(SwingConstants.CENTER);
        txtPortNumber.setColumns(10);
        txtPortNumber.setBounds(101, 163, 116, 33);
        contentPane.add(txtPortNumber);

        btnConnect = new JButton("Connect");
        btnConnect.setBounds(12, 223, 205, 38);
        contentPane.add(btnConnect);

        Myaction action = new Myaction();
        btnConnect.addActionListener(action);
        txtUserName.addActionListener(action);
        txtIpAddress.addActionListener(action);
        txtPortNumber.addActionListener(action);
    }

    // 닉네임 검사. 문제가 없으면 null, 문제가 있으면 안내 문구를 돌려준다
    // (서버도 같은 검사를 하지만, 미리 막아서 쓸데없는 접속을 줄인다)
    public static String checkUserName(String name) {
        if (name.isEmpty()) return "닉네임을 입력하세요.";
        if (name.matches(".*\\s.*") || name.contains(",")) return "닉네임에는 공백과 쉼표(,)를 쓸 수 없습니다.";
        if (name.length() > MAX_NAME_LENGTH) return "닉네임은 " + MAX_NAME_LENGTH + "자 이하로 입력하세요.";
        return null;
    }

    class Myaction implements ActionListener // 내부클래스로 액션 이벤트 처리 클래스
    {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (!btnConnect.isEnabled()) return; // 접속 중에 Enter를 또 누른 경우 무시

            String username = txtUserName.getText().trim();
            String ip_addr = txtIpAddress.getText().trim();
            String port_no = txtPortNumber.getText().trim();

            String error = checkUserName(username);
            if (error != null) {
                showWarning(error);
                txtUserName.requestFocus();
                return;
            }
            if (ip_addr.isEmpty()) {
                showWarning("서버 IP 주소를 입력하세요.");
                txtIpAddress.requestFocus();
                return;
            }
            int port;
            try {
                port = Integer.parseInt(port_no);
                if (port < 1 || port > 65535) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                showWarning("포트 번호는 1~65535 사이의 숫자로 입력하세요.");
                txtPortNumber.requestFocus();
                return;
            }

            btnConnect.setEnabled(false);
            btnConnect.setText("접속 중...");

            LiarMindClientView view = new LiarMindClientView(username, LiarMindClientMain.this);
            try {
                view.connect(ip_addr, port);
                // 여기서는 아직 창을 바꾸지 않는다.
                // 서버가 /loginok를 보내면 view가 게임 화면을 열고 이 창을 숨긴다 (/reject면 loginFailed 호출)
            } catch (IOException ex) {
                view.dispose();
                loginFailed("서버에 접속할 수 없습니다.\n서버가 켜져 있는지, IP와 포트가 맞는지 확인하세요.");
            }
        }
    }

    private void showWarning(String msg) {
        JOptionPane.showMessageDialog(this, msg, "입력 확인", JOptionPane.WARNING_MESSAGE);
    }

    // 접속 실패 또는 거부: 이유를 띄우고 다시 입력할 수 있게 한다
    public void loginFailed(String msg) {
        returnToLogin();
        JOptionPane.showMessageDialog(this, msg, "접속 실패", JOptionPane.WARNING_MESSAGE);
    }

    // 게임 화면에서 나가기를 눌렀을 때도 이 화면으로 돌아온다
    public void returnToLogin() {
        btnConnect.setEnabled(true);
        btnConnect.setText("Connect");
        setVisible(true);
        txtUserName.requestFocus();
    }
}
