// LiarMindTestClient.java (서버 테스트용 콘솔 클라이언트)
// 팀원 B의 GUI 클라이언트가 완성되기 전에 서버를 혼자 테스트하기 위한 프로그램
// 실행 후 콘솔에 명령어를 직접 입력하면 그대로 서버로 전송된다. 예) /chat 안녕  /exit
// 이클립스: Run Configurations > Arguments 에 "닉네임" 또는 "닉네임 IP 포트" 입력

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class LiarMindTestClient {
    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "tester";
        String ip = args.length > 1 ? args[1] : "127.0.0.1";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 30000;

        try (Socket socket = new Socket(ip, port);
             Scanner sc = new Scanner(System.in)) {
            socket.setTcpNoDelay(true);
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            // 서버 메시지를 받아서 출력하는 스레드 (수업 예제의 ListenNetwork와 같은 역할)
            Thread listen = new Thread(() -> {
                try {
                    while (true) {
                        System.out.println("[받음] " + dis.readUTF());
                    }
                } catch (IOException e) {
                    System.out.println("서버와 연결이 끊어졌습니다.");
                    System.exit(0);
                }
            });
            listen.setDaemon(true);
            listen.start();

            dos.writeUTF("/login " + name);
            System.out.println(name + "(으)로 접속. 명령어를 입력하세요. (예: /chat 안녕, /exit)");

            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                dos.writeUTF(line);
                if (line.equals("/exit")) break;
            }
            Thread.sleep(300); // 마지막 응답을 볼 시간
        } catch (IOException | InterruptedException e) {
            System.out.println("접속 실패: " + e.getMessage());
        }
    }
}