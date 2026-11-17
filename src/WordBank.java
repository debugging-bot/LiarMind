// WordBank.java (제시어 목록) - 3주차 새로 작성
//  - 프로젝트 폴더의 words.txt를 읽어서 "카테고리 -> 제시어들"로 저장한다.
//  - words.txt 한 줄 형식: 카테고리,제시어   (# 으로 시작하는 줄과 빈 줄은 무시)
//  - 파일이 없거나 읽을 수 없으면 아래 기본 목록을 쓴다 (서버가 멈추지 않게)
//  - 서버 시작 때 한 번 만들고 그 뒤에는 읽기만 하므로 동기화가 필요 없다

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class WordBank {
    private final Map<String, List<String>> map = new LinkedHashMap<>();
    private String source; // 어디서 읽었는지 (서버 로그용)

    public WordBank(String fileName) {
        Path path = Paths.get(fileName);
        try {
            // 한글이 깨지지 않도록 UTF-8로 읽는다
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                add(line);
            }
            source = path.toAbsolutePath().toString();
        } catch (IOException e) {
            source = "기본 목록 (" + fileName + " 읽기 실패: " + e.getMessage() + ")";
        }
        if (map.isEmpty()) {
            add("동물,호랑이");
            add("동물,기린");
            add("음식,떡볶이");
            add("음식,피자");
            add("장소,놀이공원");
            add("장소,도서관");
            if (!source.startsWith("기본")) source = "기본 목록 (" + fileName + "에 제시어가 없음)";
        }
    }

    private void add(String line) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) return;
        String[] cw = line.split(",", 2);
        if (cw.length < 2) return;
        String category = cw[0].trim();
        String word = cw[1].trim();
        // 메시지는 공백으로 나누므로 카테고리에는 공백이 있으면 안 된다
        if (category.isEmpty() || word.isEmpty() || category.contains(" ")) return;
        map.computeIfAbsent(category, k -> new ArrayList<>()).add(word);
    }

    // 카테고리 하나를 고르고 그 안에서 제시어 하나를 고른다 -> {카테고리, 제시어}
    public String[] pick(Random random) {
        List<String> categories = new ArrayList<>(map.keySet());
        String category = categories.get(random.nextInt(categories.size()));
        List<String> list = map.get(category);
        return new String[] { category, list.get(random.nextInt(list.size())) };
    }

    public String summary() {
        int total = 0;
        for (List<String> l : map.values()) total += l.size();
        return map.size() + "개 카테고리, 제시어 " + total + "개 - " + source;
    }
}
