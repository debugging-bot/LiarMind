// LiarMindClientView.java (라이어마인드 게임 화면)
// 수업 예제 JavaChatClientView.java를 바탕으로 확장
//  - 수업 예제와 같은 점: Socket 연결, DataInputStream/DataOutputStream의 readUTF()/writeUTF(),
//                       서버 메시지를 받는 ListenNetwork 스레드, SendMessage(), AppendText(), Myaction
//  - 1주차에 추가한 점: 게임 화면 배치(그림판 자리, 프로필 8칸, 채팅창, 버튼),
//                       /login 후 /loginok·/reject 처리, /users로 프로필 칸 채우기와 방장 표시,
//                       채팅을 "/chat 내용" 형식으로 변경, /chat·/system 표시, 명령어 switch 처리,
//                       화면 변경은 EDT에 맡김(SwingUtilities.invokeLater), 나가기(/exit)와 서버 연결 끊김 처리
//  - 2주차에 추가한 점: 그림판 자리를 DrawPanel로 교체, 색상 5개·굵기 3단계·지우개·전체 지우기 버튼,
//                       /draw·/clear 받아서 그리기, 숫자가 아닌 /draw는 무시
//  - 3주차에 추가한 점: 바퀴 수 선택과 /start, /word 제시어 표시(라이어는 카테고리만),
//                       /turn으로 내 차례에만 그리기·다음 버튼, 그리는 사람 프로필 강조,
//                       /votestart 투표 창, /guessstart 라이어 정답 창, /result 결과 창, /score 점수 표시
//  - WindowBuilder: 화면 배치는 모두 생성자 안에 setBounds로 작성해서 Design 탭에서 보이고 수정할 수 있다

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
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
    private DrawPanel drawPanel;    // 그림판 (2주차)
    private JPanel colorPanel;      // 색상·굵기 버튼 묶음 (2주차)
    private JButton btnBlack;
    private JButton btnRed;
    private JButton btnBlue;
    private JButton btnGreen;
    private JButton btnYellow;
    private JButton btnEraser;
    private JButton btnClear;
    private JButton btnThin;
    private JButton btnMedium;
    private JButton btnThick;
    private JLabel lblTool;         // 지금 고른 도구 표시
    private JTextArea textArea;     // 채팅 출력창 (4주차에 이모티콘용 JTextPane으로 교체)
    private JTextField txtInput;
    private JButton btnSend;
    private JLabel lblRounds;       // "바퀴 수" (3주차, 방장에게만 보임)
    private JComboBox<String> cmbRounds;
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

    // 게임 상태 (3주차) - 게임의 판단은 모두 서버가 하고, 클라이언트는 받은 메시지에 따라 화면만 바꾼다
    private String[] userNames = new String[0];            // 마지막으로 받은 /users 목록
    private boolean inGame = false;                         // /word ~ /result 사이
    private String myCategory = "";                         // /word로 받은 카테고리
    private boolean amLiar = false;                         // /word의 제시어가 ?였는지
    private String currentDrawer = null;                    // /turn으로 받은 지금 그리는 사람
    private final Map<String, Integer> scores = new HashMap<>(); // /score로 받은 점수
    private JDialog voteDialog;                             // 열려 있는 투표 창
    private JDialog guessDialog;                            // 열려 있는 정답 입력 창

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

        // ── 가운데: 그림판 (560×420) ──
        // 테두리는 그림판 바깥에 따로 그린다 (그림판 안에 테두리를 넣으면 그림 좌표가 테두리 두께만큼 밀림)
        JPanel drawFrame = new JPanel();
        drawFrame.setBorder(new LineBorder(Color.GRAY, 1));
        drawFrame.setBounds(259, 81, 562, 422);
        drawFrame.setLayout(null);
        contentPane.add(drawFrame);

        drawPanel = new DrawPanel();
        drawPanel.setBounds(1, 1, 560, 420);
        drawFrame.add(drawPanel);

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
        colorPanel.setBorder(new TitledBorder(null, "펜", TitledBorder.LEADING, TitledBorder.TOP, null, null));
        colorPanel.setBounds(640, 520, 420, 76);
        contentPane.add(colorPanel);
        colorPanel.setLayout(null);

        // 1줄: 색상 5개, 지우개, 전체 지우기
        btnBlack = new JButton("");
        btnBlack.setToolTipText("검정");
        btnBlack.setBackground(Color.BLACK);
        btnBlack.setBounds(12, 18, 30, 24);
        colorPanel.add(btnBlack);

        btnRed = new JButton("");
        btnRed.setToolTipText("빨강");
        btnRed.setBackground(Color.RED);
        btnRed.setBounds(48, 18, 30, 24);
        colorPanel.add(btnRed);

        btnBlue = new JButton("");
        btnBlue.setToolTipText("파랑");
        btnBlue.setBackground(Color.BLUE);
        btnBlue.setBounds(84, 18, 30, 24);
        colorPanel.add(btnBlue);

        btnGreen = new JButton("");
        btnGreen.setToolTipText("초록");
        btnGreen.setBackground(new Color(0, 160, 0));
        btnGreen.setBounds(120, 18, 30, 24);
        colorPanel.add(btnGreen);

        btnYellow = new JButton("");
        btnYellow.setToolTipText("노랑");
        btnYellow.setBackground(new Color(255, 210, 0));
        btnYellow.setBounds(156, 18, 30, 24);
        colorPanel.add(btnYellow);

        btnEraser = new JButton("지우개");
        btnEraser.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        btnEraser.setBounds(200, 18, 80, 24);
        colorPanel.add(btnEraser);

        btnClear = new JButton("전체 지우기");
        btnClear.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        btnClear.setBounds(286, 18, 120, 24);
        colorPanel.add(btnClear);

        // 2줄: 굵기 3단계, 지금 도구 표시
        btnThin = new JButton("가늘게");
        btnThin.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        btnThin.setBounds(12, 46, 74, 24);
        colorPanel.add(btnThin);

        btnMedium = new JButton("보통");
        btnMedium.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        btnMedium.setBounds(92, 46, 74, 24);
        colorPanel.add(btnMedium);

        btnThick = new JButton("굵게");
        btnThick.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        btnThick.setBounds(172, 46, 74, 24);
        colorPanel.add(btnThick);

        lblTool = new JLabel("현재: 검정 · 굵기 3");
        lblTool.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        lblTool.setBounds(258, 46, 150, 24);
        colorPanel.add(lblTool);

        lblRounds = new JLabel("바퀴 수");
        lblRounds.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        lblRounds.setBounds(640, 606, 60, 28);
        contentPane.add(lblRounds);

        cmbRounds = new JComboBox<String>();
        cmbRounds.setModel(new DefaultComboBoxModel<String>(new String[] {"1바퀴", "2바퀴", "3바퀴"}));
        cmbRounds.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        cmbRounds.setBounds(700, 606, 145, 28);
        contentPane.add(cmbRounds);

        btnStart = new JButton("게임 시작");
        btnStart.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        btnStart.setBounds(640, 640, 205, 56);
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
        lblRounds.setVisible(false);
        cmbRounds.setVisible(false);
        cmbRounds.setSelectedIndex(1); // 기본 2바퀴
        btnNext.setEnabled(false);  // 내 차례일 때만 켜짐 (/turn)

        drawPanel.setView(this);    // 그림판이 서버로 /draw를 보낼 수 있게 연결

        ToolAction tool = new ToolAction();
        JButton[] toolButtons = { btnBlack, btnRed, btnBlue, btnGreen, btnYellow,
                                  btnEraser, btnClear, btnThin, btnMedium, btnThick };
        for (JButton b : toolButtons) {
            b.setFocusPainted(false);
            b.addActionListener(tool);
        }
        JButton[] colorButtons = { btnBlack, btnRed, btnBlue, btnGreen, btnYellow };
        for (JButton b : colorButtons) {
            b.setOpaque(true);                          // 운영체제 테마에서도 배경색이 보이게
            b.setBorder(new LineBorder(Color.GRAY, 1));
        }

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
                // 방장 여부, 인원, 바퀴 수는 서버가 한 번 더 검사한다 (README: /start 바퀴수)
                SendMessage("/start " + (cmbRounds.getSelectedIndex() + 1));
            }
        });

        btnNext.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                btnNext.setEnabled(false); // 두 번 눌러 차례를 두 칸 넘기지 않게 (다음 /turn에서 다시 결정)
                SendMessage("/next");
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
                setDrawing(true); // 대기실에서는 누구나 그릴 수 있음 (서버도 같은 규칙, 게임 시작 때 /clear)
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

            case "/draw": // "/draw x1 y1 x2 y2 색RGB 굵기" → 그림판에 선분 하나
                handleDraw(body);
                break;

            case "/clear": // 그림판 전체 지우기
                drawPanel.clearCanvas();
                break;

            case "/word": // "/word 카테고리 제시어" (라이어는 제시어 자리에 ?)
                handleWord(body);
                break;

            case "/turn": // "/turn 이름 바퀴 제한시간"
                handleTurn(body);
                break;

            case "/votestart": // 투표 시작
                currentDrawer = null;
                setDrawing(false);
                btnNext.setEnabled(false);
                refreshSlots();
                lblStatus.setText("투표 중 · 라이어라고 생각하는 사람을 고르세요");
                openVoteDialog();
                break;

            case "/guessstart": { // "/guessstart 라이어이름" → 라이어에게만 정답 입력 창
                closeDialog(voteDialog);
                String liar = body.trim();
                if (liar.equals(UserName)) {
                    lblStatus.setText("당신이 지목되었습니다! 제시어를 맞히세요");
                    openGuessDialog();
                } else {
                    lblStatus.setText("라이어 " + liar + "님이 제시어를 맞히는 중");
                }
                break;
            }

            case "/result": // "/result 승리팀 라이어 제시어"
                handleResult(body);
                break;

            case "/score": // "/score 이름:점수,이름:점수"
                handleScore(body);
                break;

            default:
                AppendText("(처리하지 않은 메시지) " + msg);
                break;
        }
    }

    // "/draw" 뒤의 값 6개를 숫자로 바꿔서 그린다
    // 개수가 다르거나 숫자가 아니면 무시한다 (잘못된 메시지 하나 때문에 수신 처리가 멈추지 않게)
    private void handleDraw(String body) {
        String[] v = body.trim().split(" ");
        if (v.length != 6) return;
        try {
            int x1 = Integer.parseInt(v[0]);
            int y1 = Integer.parseInt(v[1]);
            int x2 = Integer.parseInt(v[2]);
            int y2 = Integer.parseInt(v[3]);
            int rgb = Integer.parseInt(v[4]);
            int w = Integer.parseInt(v[5]);
            if (w < 1 || w > 50) return; // 말도 안 되는 굵기도 무시
            drawPanel.drawLine(x1, y1, x2, y2, rgb, w);
        } catch (NumberFormatException e) {
            // 숫자가 아닌 값이 섞인 메시지는 그리지 않는다
        }
    }

    // 펜 버튼: 색상, 지우개, 굵기는 내 그림판 설정만 바꾸고, 전체 지우기만 서버로 보낸다
    class ToolAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            Object src = e.getSource();
            if (src == btnBlack)       { drawPanel.setPenColor(Color.BLACK); colorName = "검정"; }
            else if (src == btnRed)    { drawPanel.setPenColor(Color.RED); colorName = "빨강"; }
            else if (src == btnBlue)   { drawPanel.setPenColor(Color.BLUE); colorName = "파랑"; }
            else if (src == btnGreen)  { drawPanel.setPenColor(btnGreen.getBackground()); colorName = "초록"; }
            else if (src == btnYellow) { drawPanel.setPenColor(btnYellow.getBackground()); colorName = "노랑"; }
            else if (src == btnEraser) { drawPanel.setEraser(true); }
            else if (src == btnThin)   { drawPanel.setPenWidth(3); penWidth = 3; }
            else if (src == btnMedium) { drawPanel.setPenWidth(6); penWidth = 6; }
            else if (src == btnThick)  { drawPanel.setPenWidth(12); penWidth = 12; }
            else if (src == btnClear)  { SendMessage("/clear"); } // 내 화면도 서버가 돌려준 /clear로 지워진다
            updateToolLabel();
        }
    }

    private String colorName = "검정";
    private int penWidth = 3;

    private void updateToolLabel() {
        if (drawPanel.isEraser()) lblTool.setText("현재: 지우개");
        else lblTool.setText("현재: " + colorName + " · 굵기 " + penWidth);
    }

    // "/users 민지,철수,영희" → 앞에서부터 프로필 칸 채우기, 첫 번째가 방장
    private void updateUsers(String body) {
        String[] names = body.trim().isEmpty() ? new String[0] : body.trim().split(",");
        userNames = names;
        userCount = names.length;
        isHost = names.length > 0 && names[0].equals(UserName);
        refreshSlots();
        updateHostControls();
        if (!inGame) showWaitingStatus(); // 게임 중에 누가 나가도 위쪽 진행 표시는 그대로 둔다
    }

    // 프로필 칸 8개를 다시 그린다: 이름, 방장, 나, 점수, 그리는 사람 강조
    // (/users, /turn, /score, /result 어디서 불려도 같은 결과가 나오도록 저장해 둔 상태로 그린다)
    private void refreshSlots() {
        for (int i = 0; i < slots.length; i++) {
            if (i < userNames.length) {
                String n = userNames[i];
                slots[i].setUser(n, i == 0, n.equals(UserName));
                slots[i].setScore(scores.getOrDefault(n, 0));
                slots[i].setTurn(n.equals(currentDrawer));
            } else {
                slots[i].clear(); // 남은 칸은 비운다 (누가 나가면 뒤 사람이 앞으로 당겨짐)
            }
        }
    }

    // 방장 화면에만, 그리고 대기 중일 때만 바퀴 수 선택과 시작 버튼을 보여준다
    private void updateHostControls() {
        boolean show = isHost && !inGame;
        btnStart.setVisible(show);
        lblRounds.setVisible(show);
        cmbRounds.setVisible(show);
    }

    private void showWaitingStatus() {
        lblStatus.setText("대기 중 (" + userCount + "/" + slots.length + "명)" + (isHost ? "  ·  내가 방장입니다" : ""));
    }

    // 그릴 수 있는지: 그림판과 전체 지우기 버튼을 함께 켜고 끈다
    // (서버도 게임 중에는 차례인 사람의 /draw·/clear만 받으므로, 화면에서도 미리 막아 둔다)
    private void setDrawing(boolean can) {
        drawPanel.setCanDraw(can);
        btnClear.setEnabled(can);
    }

    // "/word 과일 사과" → 제시어 표시, "/word 과일 ?" → 라이어 표시 (게임 시작)
    private void handleWord(String body) {
        String[] w = body.trim().split(" ", 2);
        myCategory = w[0];
        String word = w.length > 1 ? w[1].trim() : "?";
        amLiar = word.equals("?");
        inGame = true;
        currentDrawer = null;
        setDrawing(false);              // 첫 /turn이 올 때까지는 아무도 못 그림
        updateHostControls();           // 게임 중에는 시작 버튼 숨김
        if (amLiar) {
            lblWord.setText("당신은 라이어입니다 (카테고리: " + myCategory + ")");
            lblWord.setBackground(new Color(255, 225, 225));
        } else {
            lblWord.setText("제시어: " + word + "  (카테고리: " + myCategory + ")");
            lblWord.setBackground(new Color(225, 245, 225));
        }
    }

    // "/turn 민지 1 20" → 내 차례면 그리기와 다음 버튼을 켜고, 아니면 끈다
    private void handleTurn(String body) {
        String[] t = body.trim().split(" ");
        if (t.length < 2) return;
        currentDrawer = t[0];
        String round = t[1];
        String limit = t.length > 2 ? t[2] : "";
        boolean myTurn = currentDrawer.equals(UserName);
        setDrawing(myTurn);
        btnNext.setEnabled(myTurn);
        refreshSlots();                 // 그리는 사람 칸 강조
        String time = limit.isEmpty() ? "" : "  (" + limit + "초)";
        if (myTurn) lblStatus.setText(round + "바퀴 · 내 차례입니다! 그린 뒤 '다음 차례'를 누르세요" + time);
        else lblStatus.setText(round + "바퀴 · " + currentDrawer + " 차례" + time);
    }

    // 투표 창: 나를 뺀 참가자 이름 버튼. 누르면 /vote 이름을 보내고 닫는다
    // 모달이 아닌 창으로 띄운다 (창이 떠 있는 동안에도 채팅·시스템 메시지를 계속 받을 수 있게)
    private void openVoteDialog() {
        closeDialog(voteDialog);
        voteDialog = new JDialog(this, "투표", false);
        voteDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        voteDialog.getContentPane().setLayout(new BorderLayout(0, 10));

        JLabel guide = new JLabel("라이어라고 생각하는 사람을 고르세요 (30초)", SwingConstants.CENTER);
        guide.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        guide.setBorder(new EmptyBorder(14, 14, 0, 14));
        voteDialog.getContentPane().add(guide, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new GridLayout(0, 2, 8, 8));
        buttons.setBorder(new EmptyBorder(4, 14, 14, 14));
        for (String n : userNames) {
            if (n.equals(UserName)) continue; // 자기 자신에게는 투표할 수 없음
            JButton b = new JButton(n);
            b.setFont(new Font("맑은 고딕", Font.BOLD, 14));
            b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    SendMessage("/vote " + n);
                    voteDialog.dispose();
                    lblStatus.setText("투표 완료 · " + n + "님에게 투표했습니다");
                }
            });
            buttons.add(b);
        }
        voteDialog.getContentPane().add(buttons, BorderLayout.CENTER);
        voteDialog.pack();
        voteDialog.setLocationRelativeTo(this);
        voteDialog.setVisible(true);
    }

    // 라이어 정답 창: 단어를 입력하고 확인(또는 Enter)하면 /guess 단어를 보낸다
    private void openGuessDialog() {
        closeDialog(guessDialog);
        guessDialog = new JDialog(this, "제시어 맞히기", false);
        guessDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        guessDialog.getContentPane().setLayout(new BorderLayout(0, 10));

        JLabel guide = new JLabel("<html><center>라이어로 지목되었습니다!<br>제시어를 맞히면 라이어가 이깁니다. (카테고리: "
                + myCategory + ", 20초)</center></html>", SwingConstants.CENTER);
        guide.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        guide.setBorder(new EmptyBorder(14, 14, 0, 14));
        guessDialog.getContentPane().add(guide, BorderLayout.NORTH);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        JTextField txtGuess = new JTextField(14);
        txtGuess.setFont(new Font("맑은 고딕", Font.PLAIN, 15));
        JButton btnOk = new JButton("확인");
        ActionListener submit = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String g = txtGuess.getText().trim();
                if (g.isEmpty()) return;
                SendMessage("/guess " + g);
                guessDialog.dispose();
            }
        };
        txtGuess.addActionListener(submit);
        btnOk.addActionListener(submit);
        row.add(txtGuess);
        row.add(btnOk);
        guessDialog.getContentPane().add(row, BorderLayout.CENTER);
        guessDialog.pack();
        guessDialog.setLocationRelativeTo(this);
        guessDialog.setVisible(true);
        txtGuess.requestFocusInWindow();
    }

    // "/result LIAR 민지 사과" → 대기 상태로 되돌리고 결과 창을 띄운다
    private void handleResult(String body) {
        String[] r = body.trim().split(" ", 3);
        String winner = r[0];
        String liar = r.length > 1 ? r[1] : "?";
        String word = r.length > 2 ? r[2] : "?";

        // 대기 상태로 되돌리기 (결과 창을 닫기 전에 해 둔다: 그 사이 새 게임이 시작돼도 꼬이지 않게)
        closeDialog(voteDialog);
        closeDialog(guessDialog);
        boolean wasLiar = amLiar;
        inGame = false;
        amLiar = false;
        currentDrawer = null;
        btnNext.setEnabled(false);
        setDrawing(true);               // 대기실에서는 다시 누구나 그릴 수 있음
        updateHostControls();
        refreshSlots();
        showWaitingStatus();
        lblWord.setText("방장이 게임을 시작하기를 기다리는 중입니다");
        lblWord.setBackground(new Color(255, 248, 220));

        String title, mine;
        if (winner.equals("LIAR")) {
            title = "라이어 승리!";
            mine = wasLiar ? "당신(라이어)이 이겼습니다." : "시민 팀이 졌습니다.";
        } else if (winner.equals("CITIZEN")) {
            title = "시민 승리!";
            mine = wasLiar ? "당신(라이어)이 졌습니다." : "시민 팀이 이겼습니다.";
        } else {
            title = "게임이 중단되었습니다";
            mine = "라이어가 나갔거나 인원이 3명보다 적어졌습니다.";
        }
        AppendText("[결과] " + title + " 라이어: " + liar + ", 제시어: " + word);

        JDialog dlg = new JDialog(this, "게임 결과", false);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JLabel content = new JLabel("<html><center><font size='6'><b>" + title + "</b></font><br><br>"
                + "라이어: " + liar + "<br>제시어: " + word + "<br><br>" + mine + "</center></html>", SwingConstants.CENTER);
        content.setFont(new Font("맑은 고딕", Font.PLAIN, 15));
        content.setBorder(new EmptyBorder(20, 30, 10, 30));
        dlg.getContentPane().add(content, BorderLayout.CENTER);
        JButton ok = new JButton("확인");
        ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dlg.dispose();
            }
        });
        JPanel south = new JPanel();
        south.add(ok);
        dlg.getContentPane().add(south, BorderLayout.SOUTH);
        // 결과 창을 닫으면 내 그림판을 비운다 (그 사이 새 게임이 시작됐다면 서버의 /clear로 이미 비워졌으므로 건드리지 않음)
        dlg.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (!inGame) drawPanel.clearCanvas();
            }
        });
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // "/score 민지:3,철수:1" → 점수를 저장하고 프로필 칸에 표시
    private void handleScore(String body) {
        scores.clear();
        for (String item : body.trim().split(",")) {
            String[] kv = item.split(":");
            if (kv.length != 2) continue;
            try {
                scores.put(kv[0], Integer.parseInt(kv[1].trim()));
            } catch (NumberFormatException e) {
                // 숫자가 아니면 그 사람만 건너뛴다
            }
        }
        refreshSlots();
    }

    private void closeDialog(JDialog d) {
        if (d != null && d.isDisplayable()) d.dispose();
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
        closeDialog(voteDialog);
        closeDialog(guessDialog);
        setDrawing(false);
        txtInput.setEnabled(false);
        btnSend.setEnabled(false);
        btnStart.setVisible(false);
        lblRounds.setVisible(false);
        cmbRounds.setVisible(false);
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
