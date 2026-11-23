// DrawPanel.java (그림판) - 2주차 새로 작성
//  - 그림은 BufferedImage(canvas)에 쌓아 두고, paintComponent에서는 이 이미지 한 장만 그린다.
//    선이 아무리 많아도 다시 그리는 속도가 같고, 창을 가렸다 열어도 그림이 지워지지 않는다. (4주차 그림 저장에도 사용)
//  - 마우스로 그릴 때는 화면에 직접 그리지 않고 "/draw x1 y1 x2 y2 색 굵기"를 서버로 보내기만 한다.
//    서버가 모두에게(나 포함) 돌려준 /draw를 받았을 때 drawLine()으로 그린다 → 모든 화면의 그림이 항상 같다.
//  - WindowBuilder에서 쓸 수 있도록 매개변수 없는 생성자를 두고, 게임 화면(view)은 setView()로 나중에 연결한다.
//  - 4주차: 받은 선분을 "누가 그렸는지"와 함께 목록(strokes)에도 저장한다.
//    게임 중 "/clear 이름"을 받으면 그 사람의 선분만 목록에서 빼고, 남은 선분으로 그림을 처음부터 다시 그린다.

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

public class DrawPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    public static final int CANVAS_W = 560;     // 계획서의 그림판 크기
    public static final int CANVAS_H = 420;
    public static final int ERASER_WIDTH = 20;  // 지우개 = 흰색 굵은 선

    private BufferedImage canvas;               // 지금까지 그린 그림 전체
    private LiarMindClientView view;            // 서버로 보낼 때 view.SendMessage() 사용

    // 지금 고른 도구 (게임 화면의 색상·굵기 버튼이 바꾼다)
    private Color penColor = Color.BLACK;
    private int penWidth = 3;
    private boolean eraser = false;

    // 그릴 수 있는지 (2주차: 접속하면 누구나 / 3주차: 내 차례일 때만 true)
    private boolean canDraw = false;

    private int lastX, lastY;                   // 직전 마우스 위치 (선분의 시작점)

    // 4주차: 지금까지 받은 선분 목록 (그린 사람, 좌표, 색, 굵기). "내 그림 지우기" 때 다시 그리는 데 쓴다
    private static class Stroke {
        final String owner;                     // 그린 사람 (대기실 낙서처럼 모르면 null)
        final int x1, y1, x2, y2, rgb, w;
        Stroke(String owner, int x1, int y1, int x2, int y2, int rgb, int w) {
            this.owner = owner; this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.rgb = rgb; this.w = w;
        }
    }
    private final List<Stroke> strokes = new ArrayList<>();

    /**
     * Create the panel.
     */
    public DrawPanel() {
        setPreferredSize(new Dimension(CANVAS_W, CANVAS_H));
        setBackground(Color.WHITE);

        canvas = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_RGB);
        clearImage();

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!canDraw || view == null) return;
                lastX = clampX(e.getX());
                lastY = clampY(e.getY());
                sendLine(lastX, lastY, lastX, lastY); // 클릭만 해도 점이 찍히게 (시작점 = 끝점)
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!canDraw || view == null) return;
                int x = clampX(e.getX());
                int y = clampY(e.getY());
                if (x == lastX && y == lastY) return; // 제자리면 보내지 않음
                sendLine(lastX, lastY, x, y);         // 이전 점 → 현재 점 선분 하나
                lastX = x;                            // 다음 선분의 시작점은 지금 점
                lastY = y;
            }
        };
        addMouseListener(mouse);       // mousePressed
        addMouseMotionListener(mouse); // mouseDragged
    }

    // 게임 화면과 연결 (생성자에서 받지 않는 이유: WindowBuilder는 매개변수 없는 생성자만 쓸 수 있음)
    public void setView(LiarMindClientView view) {
        this.view = view;
    }

    public void setCanDraw(boolean canDraw) {
        this.canDraw = canDraw;
    }

    public void setPenColor(Color color) {
        this.penColor = color;
        this.eraser = false; // 색을 고르면 지우개는 꺼진다
    }

    public void setPenWidth(int width) {
        this.penWidth = width;
    }

    public void setEraser(boolean eraser) {
        this.eraser = eraser;
    }

    public boolean isEraser() {
        return eraser;
    }

    // 서버로 선분 하나 전송
    //  색은 getRGB()의 아래 24비트(RGB)만 보낸다. getRGB()는 맨 앞에 알파값(FF)이 붙어 음수가 되는데,
    //  서버의 숫자 검사가 음수를 거부할 수 있고 메시지도 짧아지기 때문이다. (검정 = 0, 흰색 = 16777215)
    private void sendLine(int x1, int y1, int x2, int y2) {
        int rgb = (eraser ? Color.WHITE : penColor).getRGB() & 0xFFFFFF;
        int w = eraser ? ERASER_WIDTH : penWidth;
        view.SendMessage(String.format("/draw %d %d %d %d %d %d", x1, y1, x2, y2, rgb, w));
    }

    // 그림판 밖으로 드래그해도 좌표가 0 ~ 크기-1 안에 있게 (음수 좌표가 서버로 가지 않게)
    private int clampX(int x) {
        return Math.max(0, Math.min(CANVAS_W - 1, x));
    }

    private int clampY(int y) {
        return Math.max(0, Math.min(CANVAS_H - 1, y));
    }

    // 서버에서 받은 /draw를 그린다 (EDT에서 호출)
    // owner: 그린 사람. 게임 중에는 지금 차례인 사람만 그릴 수 있으므로 게임 화면이 /turn으로 받은 이름을 넘겨 준다
    public void drawLine(String owner, int x1, int y1, int x2, int y2, int rgb, int w) {
        Stroke s = new Stroke(owner, x1, y1, x2, y2, rgb, w);
        strokes.add(s);
        Graphics2D g = canvas.createGraphics();
        paintStroke(g, s);
        g.dispose();
        repaint();
    }

    private void paintStroke(Graphics2D g, Stroke s) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(s.rgb));
        g.setStroke(new BasicStroke(s.w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); // 끝이 둥글어야 선분 이음새가 매끄럽다
        g.drawLine(s.x1, s.y1, s.x2, s.y2);
    }

    // 서버에서 받은 /clear: 전부 지우기 (EDT에서 호출)
    public void clearCanvas() {
        strokes.clear();
        clearImage();
        repaint();
    }

    // 4주차: 서버에서 받은 "/clear 이름": 그 사람이 그린 선분만 지운다 (EDT에서 호출)
    // 이미지에서는 특정 선만 떼어낼 수 없으므로, 목록에서 빼고 남은 선분을 받은 순서대로 처음부터 다시 그린다
    // (그 사람이 지우개로 지운 자리도 같이 되돌아온다: 지우개도 그 사람이 그린 흰 선이기 때문)
    public void clearOwner(String owner) {
        strokes.removeIf(s -> owner.equals(s.owner));
        clearImage();
        Graphics2D g = canvas.createGraphics();
        for (Stroke s : strokes) paintStroke(g, s);
        g.dispose();
        repaint();
    }

    private void clearImage() {
        Graphics2D g = canvas.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, CANVAS_W, CANVAS_H);
        g.dispose();
    }

    // 4주차 그림 저장용
    public BufferedImage getCanvas() {
        return canvas;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(canvas, 0, 0, null); // 쌓아 둔 그림 한 장만 그린다
    }
}
