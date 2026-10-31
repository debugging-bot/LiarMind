// LiarMindClientView.java (라이어마인드 게임 화면)
// 수업 예제 JavaChatClientView.java를 바탕으로 확장
//  - 수업 예제와 같은 점: Socket 연결, DataInputStream/DataOutputStream의 readUTF()/writeUTF(),
//                       서버 메시지를 받는 ListenNetwork 스레드, SendMessage(), AppendText(), Myaction
//  - 1주차에 추가한 점: 게임 화면 배치(그림판 자리, 프로필 8칸, 채팅창, 버튼),
//                       /login 후 /loginok·/reject 처리, /users로 프로필 칸 채우기와 방장 표시,
//                       채팅을 "/chat 내용" 형식으로 변경, /chat·/system 표시, 명령어 switch 처리,
//                       화면 변경은 EDT에 맡김(SwingUtilities.invokeLater), 나가기(/exit)와 서버 연결 끊김 처리
//  - WindowBuilder: 화면 배치는 모두 생성자 안에 setBounds로 작성해서 Design 탭에서 보이고 수정할 수 있다

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

public class LiarMindClientView extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final int CONNECT_TIMEOUT = 3000;   // 접속 대기 시간(ms)

    // 화면 구성 요소
    private JPanel contentPane;
    private JLabel lblStatus;       // 위쪽 상태 표시 (대기 중, 3주차: 누구 차례인지)
    private JLabel lblWord;         // 제시어 표시 (3주차)
    private JPanel drawArea;        // 그림판 자리 (2주차에 DrawPanel로 교체)
    private JPanel colorPanel;      // 색상·굵기 버튼 자리 (2주차)
    private JTextArea textArea;     // 채팅 출력창 (4주차에 이모티콘용 JTextPane으로 교체)
    private JTextField txtInput;
    private JButton btnSend;
    private JButton btnStart;
    private JButton btnNext;
    private JButton btnExit;

    // 프로필 칸 8개: 왼쪽 위→아래(0~3), 오른쪽 위→아래(4~7)
    private ProfilePanel slot0;
    private ProfilePanel slot1;
    private ProfilePanel slot2;
    private ProfilePanel slot3;
    private ProfilePanel slot4;
    private ProfilePanel slot5;
    private ProfilePanel slot6;
    private ProfilePanel slot7;
    private ProfilePanel[] slots;   // 위 8개를 순서대로 담은 배열 (/users 처리용)

    // 네트워크
    private Socket socket; // 연결소켓
    private DataInputStream dis;
    private DataOutputStream dos;

    // 상태
    private String UserName;
    private LiarMindClientMain mainFrame;      // 접속 실패·나가기 때 돌아갈 접속 화면
    private boolean loggedIn = false;          // /loginok를 받았는지
    private volatile boolean closing = false;  // 내가 연결을 끊는 중이면 true (끊김 알림을 띄우지 않음)
    private int userCount = 0;
    private boolean isHost = false;

    /**
     * Create the frame.
     * 화면만 만들고 보이지는 않는다. connect() 후 서버가 /loginok를 보내면 보인다.
     */
    public LiarMindClientView(String username, LiarMindClientMain mainFrame) {
        this.UserName = username;
        this.mainFrame = mainFrame;

        setTitle("LiarMind - " + username);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 30, 1100, 800);
        setResizable(false);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);
        contentPane.setLayout(null);

        // ── 위쪽: 상태, 제시어 ──
        lblStatus = new JLabel("대기 중");
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
        lblStatus.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        lblStatus.setBounds(260, 8, 560, 28);
        contentPane.add(lblStatus);

        lblWord = new JLabel("방장이 게임을 시작하기를 기다리는 중입니다");
        lblWord.setHorizontalAlignment(SwingConstants.CENTER);
        lblWord.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        lblWord.setOpaque(true);
        lblWord.setBackground(new Color(255, 248, 220));
        lblWord.setBorder(new LineBorder(new Color(230, 210, 160), 1, true));
        lblWord.setBounds(260, 40, 560, 34);
        contentPane.add(lblWord);

        // ── 가운데: 그림판 자리 (560×420) ──
        drawArea = new JPanel();
        drawArea.setBackground(Color.WHITE);
        drawArea.setBorder(new LineBorder(Color.GRAY, 1));
        drawArea.setBounds(260, 82, 560, 420);
        contentPane.add(drawArea);
        drawArea.setLayout(new BorderLayout(0, 0));

        JLabel lblDrawHint = new JLabel("그림판 (2주차에 DrawPanel로 교체)");
        lblDrawHint.setHorizontalAlignment(SwingConstants.CENTER);
        lblDrawHint.setForeground(Color.LIGHT_GRAY);
        drawArea.add(lblDrawHint, BorderLayout.CENTER);

        // ── 왼쪽: 프로필 칸 0~3 ──
        slot0 = new ProfilePanel();
        slot0.setBounds(20, 82, 220, 100);
        contentPane.add(slot0);

        slot1 = new ProfilePanel();
        slot1.setBounds(20, 189, 220, 100);
        contentPane.add(slot1);

        slot2 = new ProfilePanel();
        slot2.setBounds(20, 296, 220, 100);
        contentPane.add(slot2);

        slot3 = new ProfilePanel();
        slot3.setBounds(20, 403, 220, 100);
        contentPane.add(slot3);

        // ── 오른쪽: 프로필 칸 4~7 ──
        slot4 = new ProfilePanel();
        slot4.setBounds(840, 82, 220, 100);
        contentPane.add(slot4);

        slot5 = new ProfilePanel();
        slot5.setBounds(840, 189, 220, 100);
        contentPane.add(slot5);

        slot6 = new ProfilePanel();
        slot6.setBounds(840, 296, 220, 100);
        contentPane.add(slot6);

        slot7 = new ProfilePanel();
        slot7.setBounds(840, 403, 220, 100);
        contentPane.add(slot7);

        // ── 아래 왼쪽: 채팅 ──
        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBounds(20, 520, 600, 182);
        contentPane.add(scrollPane);

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        scrollPane.setViewportView(textArea);

        txtInput = new JTextField();
        txtInput.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        txtInput.setBounds(20, 710, 500, 36);
        contentPane.add(txtInput);
        txtInput.setColumns(10);

        btnSend = new JButton("보내기");
        btnSend.setBounds(526, 710, 94, 36);
        contentPane.add(btnSend);

        // ── 아래 오른쪽: 색상 버튼 자리, 시작·다음·나가기 ──
        colorPanel = new JPanel();
        colorPanel.setBorder(new TitledBorder(null, "색상 · 굵기 (2주차)", TitledBorder.LEADING, TitledBorder.TOP, null, null));
        colorPanel.setBounds(640, 520, 420, 76);
        contentPane.add(colorPanel);

        btnStart = new JButton("게임 시작");
        btnStart.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        btnStart.setBounds(640, 606, 205, 90);
        contentPane.add(btnStart);

        btnNext = new JButton("다음 차례");
        btnNext.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        btnNext.setBounds(855, 606, 205, 90);
        contentPane.add(btnNext);

        btnExit = new JButton("나가기");
        btnExit.setBounds(640, 710, 420, 36);
        contentPane.add(btnExit);

        // ── 여기부터는 화면 배치가 아닌 초기 상태와 이벤트 ──
        // (시작 버튼을 여기서 숨겨야 Design 탭에서는 버튼이 보여서 위치를 옮길 수 있다)
        slots = new ProfilePanel[] { slot0, slot1, slot2, slot3, slot4, slot5, slot6, slot7 };
        btnStart.setVisible(false); // 방장에게만 보임 (/users 받을 때 결정)
        btnNext.setEnabled(false);  // 3주차: 내 차례일 때만 켜짐

        Myaction action = new Myaction();
        btnSend.addActionListener(action);
        txtInput.addActionListener(action);

        btnStart.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (userCount < 3) {
                    JOptionPane.showMessageDialog(LiarMindClientView.this,
                            "게임을 시작하려면 3명 이상 필요합니다. (현재 " + userCount + "명)",
                            "시작 불가", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                // 3주차: SendMessage("/start " + 바퀴수 + " " + 모드);
                AppendText("[알림] 게임 시작 기능은 3주차에 서버와 함께 연결됩니다.");
            }
        });

        btnExit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                disconnect();
                dispose();
                if (mainFrame != null) mainFrame.returnToLogin(); // 접속 화면으로 돌아가서 다시 접속할 수 있음
            }
        });

        // 창의 X 버튼: 서버에 /exit를 보내고 종료 (EXIT_ON_CLOSE보다 먼저 실행됨)
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });
    }

    // 서버에 접속하고 /login을 보낸 뒤 수신 스레드를 시작한다
    // 접속 자체가 실패하면 IOException을 던져서 접속 화면이 안내하게 한다
    public void connect(String ip_addr, int port) throws IOException {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip_addr, port), CONNECT_TIMEOUT); // 잘못된 IP에서 오래 멈추지 않게
            socket.setTcpNoDelay(true); // 작은 메시지(2주차 그림 좌표)를 모으지 않고 바로 보냄
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
            dos.writeUTF("/login " + UserName); // 첫 메시지는 반드시 /login
        } catch (IOException e) {
            closing = true;
            closeSocket();
            throw e;
        }
        ListenNetwork net = new ListenNetwork();
        net.setDaemon(true);
        net.start();
    }

    // Server Message를 수신하는 스레드
    // 화면은 이 스레드에서 직접 바꾸지 않고 invokeLater로 EDT에 맡긴다
    class ListenNetwork extends Thread {
        public void run() {
            try {
                while (true) {
                    final String msg = dis.readUTF();
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            handleMessage(msg);
                        }
                    });
                    // /reject 뒤에는 서버가 연결을 끊으므로 더 읽지 않는다 (끊김 알림이 중복으로 뜨는 것 방지)
                    if (msg.startsWith("/reject")) return;
                }
            } catch (IOException e) {
                if (!closing) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            connectionLost();
                        }
                    });
                }
            }
        }
    }

    // 서버 메시지 처리 (EDT에서 실행)
    // "/명령어 나머지"로 나눠서 처리. 나머지 안에는 공백이 있어도 됨
    private void handleMessage(String msg) {
        String[] parts = msg.split(" ", 2);
        String cmd = parts[0];
        String body = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "/loginok": // 접속 성공 → 게임 화면 열기
                loggedIn = true;
                setVisible(true);
                mainFrame.setVisible(false);
                AppendText("[알림] " + UserName + "(으)로 접속했습니다.");
                txtInput.requestFocus();
                break;

            case "/reject": // 접속 거부 → 이유를 띄우고 접속 화면으로
                closing = true;
                closeSocket();
                dispose();
                mainFrame.loginFailed(rejectMessage(body.trim()));
                break;

            case "/users": // 접속자 목록 → 프로필 칸
                updateUsers(body);
                break;

            case "/system": // 안내 문구
                AppendText("[알림] " + body);
                break;

            case "/chat": { // "/chat 보낸이름 내용" → "[보낸이름] 내용"
                String[] c = body.split(" ", 2);
                String name = c[0];
                String text = c.length > 1 ? c[1] : "";
                AppendText("[" + name + "] " + text);
                break;
            }

            // 2주차: /draw, /clear
            // 3주차: /word, /turn, /votestart, /guessstart, /result
            default:
                AppendText("(처리하지 않은 메시지) " + msg);
                break;
        }
    }

    // "/users 민지,철수,영희" → 앞에서부터 프로필 칸 채우기, 첫 번째가 방장
    private void updateUsers(String body) {
        String[] names = body.trim().isEmpty() ? new String[0] : body.trim().split(",");
        userCount = names.length;
        for (int i = 0; i < slots.length; i++) {
            if (i < names.length) {
                slots[i].setUser(names[i], i == 0, names[i].equals(UserName));
            } else {
                slots[i].clear(); // 남은 칸은 비운다 (누가 나가면 뒤 사람이 앞으로 당겨짐)
            }
        }
        isHost = names.length > 0 && names[0].equals(UserName);
        btnStart.setVisible(isHost); // 방장 화면에만 시작 버튼
        lblStatus.setText("대기 중 (" + userCount + "/" + slots.length + "명)" + (isHost ? "  ·  내가 방장입니다" : ""));
    }

    // /reject 이유 코드를 안내 문구로 바꾼다 (README 표 참고)
    private String rejectMessage(String reason) {
        switch (reason) {
            case "empty":          return "닉네임을 입력하세요.";
            case "invalid":        return "닉네임에는 공백과 쉼표(,)를 쓸 수 없습니다.";
            case "toolong":        return "닉네임은 10자 이하로 입력하세요.";
            case "duplicate":      return "이미 사용 중인 닉네임입니다.";
            case "full":           return "방이 가득 찼습니다. (최대 8명)";
            case "started":        return "게임이 진행 중이라 들어갈 수 없습니다.";
            case "login_required": return "접속 절차에 문제가 있습니다. 다시 시도하세요.";
            default:               return "접속이 거부되었습니다. (" + reason + ")";
        }
    }

    // 서버가 꺼지거나 연결이 끊겼을 때 (EDT에서 실행)
    private void connectionLost() {
        closing = true;
        closeSocket();
        if (!loggedIn) { // 로그인 전에 끊김 → 접속 화면으로
            dispose();
            mainFrame.loginFailed("서버와 연결이 끊겼습니다.");
            return;
        }
        AppendText("[알림] 서버 연결이 끊겼습니다.");
        lblStatus.setText("서버 연결 끊김");
        txtInput.setEnabled(false);
        btnSend.setEnabled(false);
        btnStart.setVisible(false);
        btnNext.setEnabled(false);
        JOptionPane.showMessageDialog(this, "서버 연결이 끊겼습니다.", "연결 끊김", JOptionPane.ERROR_MESSAGE);
    }

    // 정상 퇴장: 서버에 /exit를 보내고 소켓을 닫는다 (나가기 버튼, 창 닫기)
    private void disconnect() {
        if (closing) return;
        if (loggedIn) SendMessage("/exit");
        closing = true;
        closeSocket();
    }

    private void closeSocket() {
        try {
            if (dos != null) dos.close();
            if (dis != null) dis.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // 이미 닫힌 경우 무시
        }
    }

    // 채팅 입력 후 보내기 버튼 또는 Enter → "/chat 내용" 전송
    // 내 채팅도 서버가 "/chat 내이름 내용"으로 돌려주므로 여기서 직접 출력하지 않는다
    class Myaction implements ActionListener // 내부클래스로 액션 이벤트 처리 클래스
    {
        @Override
        public void actionPerformed(ActionEvent e) {
            String text = txtInput.getText().trim();
            if (text.isEmpty()) return;
            SendMessage("/chat " + text);
            txtInput.setText("");    // 메세지를 보내고 나면 메세지 쓰는창을 비운다.
            txtInput.requestFocus(); // 메세지를 보내고 커서를 다시 텍스트 필드로 위치시킨다
        }
    }

    // 채팅창에 출력 (EDT에서만 부른다)
    public void AppendText(String msg) {
        textArea.append(msg + "\n");
        textArea.setCaretPosition(textArea.getText().length());
    }

    // Server에게 network로 전송
    // 2주차에 그림 좌표를 보낼 때도 쓰므로, 여러 곳에서 동시에 불려도 메시지가 섞이지 않게 synchronized
    public synchronized void SendMessage(String msg) {
        if (closing || dos == null) return;
        try {
            dos.writeUTF(msg);
        } catch (IOException e) {
            AppendText("[알림] 메시지를 보내지 못했습니다.");
            // 연결이 끊긴 경우 수신 스레드가 readUTF() 예외로 감지해서 connectionLost()를 부른다
        }
    }
}
