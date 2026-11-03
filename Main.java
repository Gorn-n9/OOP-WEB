import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public class Main {
    private static final Pattern JAVA_TOKENS =
            Pattern.compile(
                    "(?<flow>\\b(?:return|throw|new)\\b)"
                        + "|(?<keyword>\\b(?:abstract|class|extends|final|implements|int|interface|private|protected|public|static|void)\\b)"
                        + "|(?<annotation>@\\w+)|(?<number>\\b\\d+\\b)|(?<type>\\b[A-Z]\\w*\\b)"
                        + "|(?<method>\\b\\w+\\b(?=\\s*\\())");
    private static final List<String> TOKEN_COLORS =
            List.of("flow", "keyword", "annotation", "number", "type", "method");

    private static final List<Demo> DEMOS =
            List.of(
                    new Demo("delivery", "ค่าส่งพัสดุ", "น้ำหนัก (กก.)", 2, 30),
                    new Demo("parking", "ค่าจอดรถ", "เวลาจอด (ชั่วโมง)", 4, 24),
                    new Demo("ticket", "ราคาตั๋ว", "จำนวนผู้เข้าชม (คน)", 3, 20),
                    new Demo("attack", "พลังโจมตีในเกม", "พลังพื้นฐาน", 8, 100));

    private record Demo(String id, String title, String label, int initial, int max) {}

    private record Quote(String name, int value, String rule) {}

    private record Comparison(String unit, Quote first, Quote second, CodeBlock... blocks) {}

    private record CodeBlock(String title, String explanation, String code) {}

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8765 : Integer.parseInt(args[0]);
        String template = Files.readString(Path.of("index.html"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", exchange -> serve(exchange, template));
        server.start();
        System.out.println("OOP Lab: http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void serve(HttpExchange exchange, String template) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            Demo demo =
                    path.equals("/")
                            ? DEMOS.getFirst()
                            : DEMOS.stream()
                                    .filter(candidate -> path.equals("/" + candidate.id()))
                                    .findFirst()
                                    .orElse(null);
            if (demo == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            int input = demo.initial();
            int status = 200;
            String message = "";
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                try {
                    String normalized =
                            demo.id().equals("delivery")
                                    ? query.replaceFirst("^weight=", "input=")
                                    : query;
                    if (!normalized.matches("input=[0-9]{1,3}")) {
                        throw new IllegalArgumentException();
                    }
                    input = Integer.parseInt(normalized.substring("input=".length()));
                    if (input < 1 || input > demo.max()) {
                        throw new IllegalArgumentException();
                    }
                } catch (IllegalArgumentException error) {
                    input = demo.initial();
                    status = 400;
                    message =
                            "<p role=\"alert\">กรุณาใส่จำนวนเต็มระหว่าง 1 ถึง "
                                    + demo.max()
                                    + "</p>";
                }
            }

            Comparison result = compare(demo.id(), input);
            StringBuilder navigation = new StringBuilder();
            for (Demo link : DEMOS) {
                navigation.append("<a href=\"/").append(link.id()).append("\"");
                if (link.equals(demo)) {
                    navigation.append(" aria-current=\"page\"");
                }
                navigation.append(">").append(link.title()).append("</a>");
            }

            byte[] html =
                    template.replace("{{nav}}", navigation)
                            .replace("{{id}}", demo.id())
                            .replace("{{title}}", demo.title())
                            .replace("{{label}}", demo.label())
                            .replace("{{input}}", Integer.toString(input))
                            .replace("{{max}}", Integer.toString(demo.max()))
                            .replace("{{message}}", message)
                            .replace(
                                    "{{quotes}}",
                                    card(result.first(), result.unit())
                                            + card(result.second(), result.unit()))
                            .replace("{{blocks}}", renderBlocks(result.blocks()))
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(status, html.length);
            exchange.getResponseBody().write(html);
        }
    }

    private static String card(Quote quote, String unit) {
        return """
               <article class="card quote">
                 <h3>%s</h3>
                 <p class="price">%d <small>%s</small></p>
                 <p>%s</p>
               </article>
               """
                .formatted(quote.name(), quote.value(), unit, quote.rule());
    }

    private static String renderBlocks(CodeBlock[] blocks) {
        StringBuilder html = new StringBuilder();
        int step = 1;
        for (CodeBlock block : blocks) {
            html.append(
                    """
                    <li class="card code-block">
                      <h3><span class="step">%02d</span> %s</h3>
                      <div class="editor">
                        <div class="editor-label">Java</div>
                        <pre tabindex="0"><code>%s</code></pre>
                      </div>
                      <p>%s</p>
                    </li>
                    """
                            .formatted(
                                    step++,
                                    block.title(),
                                    highlightJava(block.code()),
                                    block.explanation()));
        }
        return html.toString();
    }

    private static String highlightJava(String code) {
        StringBuilder html = new StringBuilder();
        for (String line : code.stripTrailing().split("\\R")) {
            if (!html.isEmpty()) {
                html.append('\n');
            }
            html.append("<span class=\"code-line\">");
            var tokens = JAVA_TOKENS.matcher(line);
            int cursor = 0;
            while (tokens.find()) {
                html.append(escapeCode(line.substring(cursor, tokens.start())));
                String color =
                        TOKEN_COLORS.stream()
                                .filter(name -> tokens.group(name) != null)
                                .findFirst()
                                .orElseThrow();
                html.append("<span class=\"token-")
                        .append(color)
                        .append("\">")
                        .append(escapeCode(tokens.group()))
                        .append("</span>");
                cursor = tokens.end();
            }
            html.append(escapeCode(line.substring(cursor))).append("</span>");
        }
        return html.toString();
    }

    private static String escapeCode(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Comparison compare(String id, int input) {
        return switch (id) {
            case "delivery" -> {
                Delivery standard = new StandardDelivery(input);
                Delivery express = new ExpressDelivery(input);
                yield new Comparison(
                        "บาท",
                        new Quote(standard.name(), standard.calculateFee(), standard.rate()),
                        new Quote(express.name(), express.calculateFee(), express.rate()),
                        new CodeBlock(
                                "คลาสแม่เก็บน้ำหนัก · Encapsulation และ Abstraction",
                                "<code>Delivery</code> เก็บน้ำหนักในฟิลด์ <code>private"
                                    + " final</code> และตรวจช่วงใน constructor ส่วนเมธอด"
                                    + " <code>abstract</code> กำหนดให้คลาสลูกมีวิธีคำนวณค่าส่ง",
                                """
                                abstract class Delivery {
                                    private final int weightKg;

                                    protected Delivery(int weightKg) {
                                        if (weightKg < 1 || weightKg > 30) {
                                            throw new IllegalArgumentException("Weight must be between 1 and 30 kg");
                                        }
                                        this.weightKg = weightKg;
                                    }

                                    protected final int weightKg() {
                                        return weightKg;
                                    }

                                    public abstract int calculateFee();
                                }
                                """),
                        new CodeBlock(
                                "คลาสลูกเขียนสูตรของตัวเอง · Inheritance",
                                "ทั้งสองคลาสสืบทอด <code>Delivery</code> จึงอ่านน้ำหนักผ่าน"
                                    + " <code>weightKg()</code> ได้ ส่งธรรมดาและส่งด่วน override"
                                    + " เมธอดเดียวกันด้วยสูตรคนละแบบ",
                                """
                                final class StandardDelivery extends Delivery {
                                    StandardDelivery(int weightKg) {
                                        super(weightKg);
                                    }

                                    @Override
                                    public int calculateFee() {
                                        return 25 + 10 * weightKg();
                                    }
                                }

                                final class ExpressDelivery extends Delivery {
                                    ExpressDelivery(int weightKg) {
                                        super(weightKg);
                                    }

                                    @Override
                                    public int calculateFee() {
                                        return 45 + 18 * weightKg();
                                    }
                                }
                                """),
                        new CodeBlock(
                                "สร้างอ็อบเจกต์แล้วเรียกเมธอด · Polymorphism",
                                "ตัวแปรทั้งคู่มีชนิด <code>Delivery</code>"
                                    + " แต่ชี้ไปยังอ็อบเจกต์คนละคลาส Java"
                                    + " จึงเลือกสูตรตามอ็อบเจกต์เมื่อเรียก"
                                    + " <code>calculateFee()</code>",
                                """
                                Delivery standard = new StandardDelivery(input);
                                Delivery express = new ExpressDelivery(input);
                                standard.calculateFee();
                                express.calculateFee();
                                """));
            }
            case "parking" -> {
                ParkingRate hourly = new HourlyParking();
                ParkingRate capped = new CappedParking();
                yield new Comparison(
                        "บาท",
                        new Quote("รายชั่วโมง", hourly.calculateFee(input), "25 บาทต่อชั่วโมง"),
                        new Quote(
                                "มีเพดานราคา",
                                capped.calculateFee(input),
                                "40 บาทต่อชั่วโมง สูงสุด 120 บาท"),
                        new CodeBlock(
                                "กำหนดวิธีเรียกคิดค่าจอด · Abstraction",
                                "<code>ParkingRate</code>"
                                    + " กำหนดให้ส่งจำนวนชั่วโมงเข้าไปและรับค่าจอดกลับมา",
                                """
                                interface ParkingRate {
                                    int calculateFee(int hours);
                                }
                                """),
                        new CodeBlock(
                                "เขียนกติกาคิดค่าจอดสองแบบ",
                                "แบบรายชั่วโมงคิด 25 บาทต่อชั่วโมง ส่วนแบบมีเพดานใช้"
                                    + " <code>Math.min()</code> เลือกระหว่างค่าที่คำนวณได้กับ 120"
                                    + " บาท",
                                """
                                final class HourlyParking implements ParkingRate {
                                    @Override
                                    public int calculateFee(int hours) {
                                        return 25 * hours;
                                    }
                                }

                                final class CappedParking implements ParkingRate {
                                    @Override
                                    public int calculateFee(int hours) {
                                        return Math.min(40 * hours, 120);
                                    }
                                }
                                """),
                        new CodeBlock(
                                "สร้างอ็อบเจกต์แล้วเรียกเมธอด · Polymorphism",
                                "ส่งจำนวนชั่วโมงเดียวกันให้ทั้งสองอ็อบเจกต์ผ่าน"
                                    + " <code>ParkingRate</code>"
                                    + " แต่เมธอดของแต่ละคลาสใช้กติกาต่างกัน",
                                """
                                ParkingRate hourly = new HourlyParking();
                                ParkingRate capped = new CappedParking();
                                hourly.calculateFee(input);
                                capped.calculateFee(input);
                                """));
            }
            case "ticket" -> {
                Ticket adult = new AdultTicket();
                Ticket student = new StudentTicket();
                yield new Comparison(
                        "บาท",
                        new Quote("ตั๋วผู้ใหญ่", adult.calculateTotal(input), "150 บาทต่อคน"),
                        new Quote("ตั๋วนักเรียน", student.calculateTotal(input), "90 บาทต่อคน"),
                        new CodeBlock(
                                "กำหนดวิธีคิดราคารวม · Abstraction",
                                "<code>Ticket</code> กำหนดให้ส่งจำนวนคนเข้าไปและรับราคารวมกลับมา",
                                """
                                interface Ticket {
                                    int calculateTotal(int people);
                                }
                                """),
                        new CodeBlock(
                                "กำหนดราคาตามประเภทตั๋ว",
                                "คลาส <code>AdultTicket</code> คิดคนละ 150 บาท ส่วน"
                                    + " <code>StudentTicket</code> คิดคนละ 90 บาท",
                                """
                                final class AdultTicket implements Ticket {
                                    @Override
                                    public int calculateTotal(int people) {
                                        return 150 * people;
                                    }
                                }

                                final class StudentTicket implements Ticket {
                                    @Override
                                    public int calculateTotal(int people) {
                                        return 90 * people;
                                    }
                                }
                                """),
                        new CodeBlock(
                                "สร้างอ็อบเจกต์แล้วเรียกเมธอด · Polymorphism",
                                "เรียก <code>calculateTotal(input)</code> เหมือนกันทั้งคู่"
                                    + " ประเภทของอ็อบเจกต์เป็นตัวกำหนดราคาต่อคน",
                                """
                                Ticket adult = new AdultTicket();
                                Ticket student = new StudentTicket();
                                adult.calculateTotal(input);
                                student.calculateTotal(input);
                                """));
            }
            case "attack" -> {
                CharacterAttack warrior = new WarriorAttack();
                CharacterAttack mage = new MageAttack();
                yield new Comparison(
                        "ดาเมจ",
                        new Quote("นักรบ", warrior.calculateDamage(input), "พลังพื้นฐาน + 10"),
                        new Quote("จอมเวท", mage.calculateDamage(input), "พลังพื้นฐาน × 2"),
                        new CodeBlock(
                                "กำหนดวิธีคำนวณดาเมจ · Abstraction",
                                "<code>CharacterAttack</code>"
                                    + " กำหนดให้รับค่าพลังพื้นฐานและคืนค่าดาเมจ",
                                """
                                interface CharacterAttack {
                                    int calculateDamage(int power);
                                }
                                """),
                        new CodeBlock(
                                "เขียนสูตรโจมตีของแต่ละตัวละคร",
                                "นักรบบวกพลังพื้นฐาน 10 หน่วย จอมเวทคูณพลังพื้นฐานด้วย 2",
                                """
                                final class WarriorAttack implements CharacterAttack {
                                    @Override
                                    public int calculateDamage(int power) {
                                        return power + 10;
                                    }
                                }

                                final class MageAttack implements CharacterAttack {
                                    @Override
                                    public int calculateDamage(int power) {
                                        return power * 2;
                                    }
                                }
                                """),
                        new CodeBlock(
                                "สร้างอ็อบเจกต์แล้วเรียกเมธอด · Polymorphism",
                                "เรียก <code>calculateDamage(input)</code> ด้วยพลังเท่ากัน Java"
                                    + " เลือกสูตรตามตัวละครที่สร้างไว้",
                                """
                                CharacterAttack warrior = new WarriorAttack();
                                CharacterAttack mage = new MageAttack();
                                warrior.calculateDamage(input);
                                mage.calculateDamage(input);
                                """));
            }
            default -> throw new IllegalArgumentException("Unknown demo: " + id);
        };
    }
}
