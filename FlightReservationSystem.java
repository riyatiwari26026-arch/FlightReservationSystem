import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * SkyWings Airlines - Flight Reservation System (Java AWT + Swing + Event Handling)
 *
 * NEW IN THIS VERSION
 *  1. Calendar date picker limited to 26 October 2026 - 26 November 2026
 *  2. Auto-formatting card number (1234 5678 9012 3456) and expiry (MM/YY) with live card preview
 *  3. Modern look: sidebar navigation, rounded cards/buttons, gradients, animated login, dark mode
 *  4. Extra features: dashboard + chart, dynamic pricing, seat extras, flight food, baggage, insurance,
 *     promo codes, loyalty tiers and points, boarding pass with barcode (save as PNG/TXT),
 *     reschedule booking, user registration, keyboard shortcuts
 *
 * Demo logins : user / user123   |   admin / admin123
 * Promo codes : SKY10, WELCOME, STUDENT, FLYHIGH
 * Compile     : javac FlightReservationSystem.java
 * Run         : java FlightReservationSystem
 */
public class FlightReservationSystem {

    // =====================================================================
    //  DUMMY DATABASE (in-memory)
    //  ---------------------------------------------------------------
    //  These collections work like simple database tables for this
    //  classroom project. No MySQL installation is required.
    //
    //  users    -> user table
    //  flights  -> flight table
    //  bookings -> booking table
    //
    //  Later, these collections can be replaced with JDBC tables without
    //  changing the Swing event-handling code.
    // =====================================================================
    static final int ROWS = 10, COLS = 6;
    static final String LETTERS = "ABCDEF";
    static final LocalDate BOOKING_START = LocalDate.of(2026, 10, 26);
    static final LocalDate BOOKING_END = LocalDate.of(2026, 11, 26);
    static final List<Flight> flights = new ArrayList<>();
    static final List<Booking> bookings = new ArrayList<>();
    static final Map<String, String> users = new HashMap<>();
    static final Map<String, Integer> points = new HashMap<>();
    static final Map<String, String> CODES = new HashMap<>();
    static final Map<String, double[]> PROMOS = new LinkedHashMap<>();   // {percent, flat, cap}
    static final Map<String, String> PROMO_TEXT = new HashMap<>();
    static final String[] MEALS = {
            "No food reserved",
            "Vegetarian meal (+Rs. 350)",
            "Non-vegetarian meal (+Rs. 450)",
            "Jain meal (+Rs. 350)",
            "Vegan meal (+Rs. 400)"
    };
    static final double[] MEAL_PRICE = {0, 350, 450, 350, 400};
    static final String[] BAGS = {"Cabin only (7 kg)", "+5 kg", "+10 kg", "+15 kg", "+20 kg"};
    static final double[] BAG_PRICE = {0, 500, 900, 1300, 1700};
    static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);
    static final DateTimeFormatter SHORT_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    static int pnrCounter = 1000;

    static class Flight {
        String id, from, to, depart, arrive;
        double price;
        private final Map<LocalDate, boolean[][]> seatMap = new HashMap<>();

        Flight(String id, String from, String to, String depart, String arrive, double price) {
            this.id = id; this.from = from; this.to = to;
            this.depart = depart; this.arrive = arrive; this.price = price;
        }

        /** Every flight operates daily; each date has its own seat map (true = booked). */
        boolean[][] seats(LocalDate d) {
            return seatMap.computeIfAbsent(d, k -> {
                boolean[][] s = new boolean[ROWS][COLS];
                Random rnd = new Random((id + k).hashCode());
                for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) s[r][c] = rnd.nextInt(100) < 22;
                return s;
            });
        }

        int seatsLeft(LocalDate d) {
            int n = 0;
            for (boolean[] row : seats(d)) for (boolean b : row) if (!b) n++;
            return n;
        }

        String durationText() {
            long m = ChronoUnit.MINUTES.between(LocalTime.parse(depart), LocalTime.parse(arrive));
            if (m <= 0) m += 24 * 60;
            return (m / 60) + "h " + (m % 60) + "m";
        }
    }

    static class Booking {
        String pnr, owner, passenger, gender, email, seat, travelClass, meal, bag, promo, status = "CONFIRMED";
        int age, row, col, pointsUsed, pointsEarned;
        boolean insurance;
        double amount;
        Flight flight;
        LocalDate date;
    }

    static class Fare {
        double base, seat, meal, bag, ins, tier, promo, pts, gst, total;
        int ptsUsed;
        String tierName = "Silver";

        String html(String cls) {
            StringBuilder s = new StringBuilder("<html><table width='100%' cellspacing='2'>");
            add(s, "Base fare (" + cls + ")", base, false);
            if (seat > 0) add(s, "Seat selection", seat, false);
            if (meal > 0) add(s, "Flight food", meal, false);
            if (bag > 0) add(s, "Extra baggage", bag, false);
            if (ins > 0) add(s, "Travel insurance", ins, false);
            if (tier > 0) add(s, tierName + " member discount", tier, true);
            if (promo > 0) add(s, "Promo discount", promo, true);
            if (pts > 0) add(s, "Loyalty points (" + ptsUsed + ")", pts, true);
            add(s, "GST (5%)", gst, false);
            return s.append("</table></html>").toString();
        }

        private static void add(StringBuilder s, String name, double v, boolean minus) {
            s.append("<tr><td>");
            if (minus) s.append("<font color='#10b981'>");
            s.append(name).append("</td><td align='right'>").append(minus ? "- " : "").append(money(v));
            if (minus) s.append("</font>");
            s.append("</td></tr>");
        }
    }

    static void seed() {
        users.put("user", "user123");
        users.put("admin", "admin123");
        points.put("user", 600);
        points.put("admin", 0);
        String[][] codes = {{"Delhi", "DEL"}, {"Mumbai", "BOM"}, {"Bangalore", "BLR"}, {"Kolkata", "CCU"},
                {"Chennai", "MAA"}, {"Dehradun", "DED"}, {"Hyderabad", "HYD"}, {"Goa", "GOI"}, {"Jaipur", "JAI"}};
        for (String[] c : codes) CODES.put(c[0], c[1]);

        // Manual flight timings: edit these values when you want to add
        // or change a flight schedule. Time format is 24-hour HH:mm.
        flights.add(new Flight("SW101", "Delhi", "Mumbai", "06:00", "08:10", 4500));
        flights.add(new Flight("SW102", "Mumbai", "Delhi", "09:30", "11:45", 4700));
        flights.add(new Flight("SW203", "Delhi", "Bangalore", "07:15", "10:05", 5600));
        flights.add(new Flight("SW204", "Bangalore", "Kolkata", "13:40", "16:10", 5200));
        flights.add(new Flight("SW305", "Dehradun", "Delhi", "08:00", "09:00", 2900));
        flights.add(new Flight("SW306", "Delhi", "Dehradun", "18:20", "19:20", 3100));
        flights.add(new Flight("SW407", "Chennai", "Hyderabad", "11:00", "12:15", 3300));
        flights.add(new Flight("SW408", "Mumbai", "Bangalore", "15:25", "17:05", 3900));
        flights.add(new Flight("SW509", "Delhi", "Goa", "10:10", "13:00", 6100));
        flights.add(new Flight("SW510", "Jaipur", "Mumbai", "19:45", "21:35", 4200));

        PROMOS.put("SKY10", new double[]{10, 0, 1000});
        PROMO_TEXT.put("SKY10", "10% off (max Rs. 1000)");
        PROMOS.put("WELCOME", new double[]{0, 500, 500});
        PROMO_TEXT.put("WELCOME", "Flat Rs. 500 off");
        PROMOS.put("STUDENT", new double[]{15, 0, 1500});
        PROMO_TEXT.put("STUDENT", "15% student discount (max Rs. 1500)");
        PROMOS.put("FLYHIGH", new double[]{20, 0, 2000});
        PROMO_TEXT.put("FLYHIGH", "20% off (max Rs. 2000)");
    }

    // =====================================================================
    //  SMALL HELPERS (pricing, formatting)
    // =====================================================================
    static String money(double v) {
        return String.format(Locale.ENGLISH, "Rs. %,.2f", v);
    }

    static String seatName(int r, int c) {
        return (r + 1) + "" + LETTERS.charAt(c);
    }

    static String code(String city) {
        return CODES.getOrDefault(city, city.substring(0, Math.min(3, city.length())).toUpperCase());
    }

    /** Returns true only for the date range required by this project. */
    static boolean isAllowedBookingDate(LocalDate date) {
        return date != null && !date.isBefore(BOOKING_START) && !date.isAfter(BOOKING_END);
    }

    static String bookingWindowText() {
        return BOOKING_START.format(SHORT_FMT) + " to " + BOOKING_END.format(SHORT_FMT);
    }

    /** Dynamic pricing: last-minute fares cost more, early-bird fares cost less. */
    static double dynFactor(LocalDate d) {
        long days = ChronoUnit.DAYS.between(BOOKING_START, d);
        double f = 1.0;
        if (days <= 2) f += 0.35;
        else if (days <= 7) f += 0.15;
        else if (days >= 30) f -= 0.10;
        DayOfWeek w = d.getDayOfWeek();
        if (w == DayOfWeek.FRIDAY || w == DayOfWeek.SUNDAY) f += 0.08;
        return f;
    }

    static String fareNote(LocalDate d) {
        long days = ChronoUnit.DAYS.between(BOOKING_START, d);
        String s = days <= 2 ? "Last-minute fares (+35%)" : days <= 7 ? "Booking within a week (+15%)"
                : days >= 30 ? "Early-bird fares (-10%)" : "Regular fares";
        DayOfWeek w = d.getDayOfWeek();
        if (w == DayOfWeek.FRIDAY || w == DayOfWeek.SUNDAY) s += "  |  Peak day (+8%)";
        return s;
    }

    static double seatExtra(int r, int c) {
        double x = 0;
        if (r < 2) x += 500;                         // extra legroom rows
        if (c == 0 || c == 5) x += 250;              // window
        else if (c == 2 || c == 3) x += 100;         // aisle
        return x;
    }

    static String seatDesc(int r, int c) {
        String t = (c == 0 || c == 5) ? "Window" : (c == 2 || c == 3) ? "Aisle" : "Middle";
        return r < 2 ? t + ", extra legroom" : t;
    }

    static String tierOf(int pts) {
        return pts >= 2000 ? "Platinum" : pts >= 500 ? "Gold" : "Silver";
    }

    static double tierPercent(int pts) {
        return pts >= 2000 ? 5 : pts >= 500 ? 3 : 0;
    }

    static String brand(String digits) {
        if (digits.startsWith("4")) return "VISA";
        if (digits.matches("^5[1-5].*")) return "MASTERCARD";
        if (digits.startsWith("6") || digits.startsWith("81") || digits.startsWith("82")) return "RUPAY";
        return "CARD";
    }

    static String ticketText(Booking b) {
        Flight f = b.flight;
        StringBuilder s = new StringBuilder();
        s.append("==============================================\n");
        s.append("          SKYWINGS AIRLINES - E-TICKET\n");
        s.append("==============================================\n");
        s.append(String.format("PNR            : %s%n", b.pnr));
        s.append(String.format("Status         : %s%n", b.status));
        s.append(String.format("Passenger      : %s (%d, %s)%n", b.passenger, b.age, b.gender));
        s.append(String.format("Email          : %s%n", b.email));
        s.append("----------------------------------------------\n");
        s.append(String.format("Flight         : %s%n", f.id));
        s.append(String.format("Route          : %s (%s) -> %s (%s)%n", f.from, code(f.from), f.to, code(f.to)));
        s.append(String.format("Date           : %s%n", b.date.format(DATE_FMT)));
        s.append(String.format("Departure      : %s   Arrival: %s%n", f.depart, f.arrive));
        s.append(String.format("Seat / Class   : %s / %s%n", b.seat, b.travelClass));
        s.append(String.format("Flight food    : %s%n", b.meal));
        s.append(String.format("Baggage        : %s%n", b.bag));
        s.append(String.format("Insurance      : %s%n", b.insurance ? "Yes" : "No"));
        s.append("----------------------------------------------\n");
        s.append(String.format("Amount Paid    : %s%n", money(b.amount)));
        s.append(String.format("Points earned  : %d%n", b.pointsEarned));
        s.append("==============================================\n");
        s.append("   Report at the airport 2 hours before departure\n");
        return s.toString();
    }

    // =====================================================================
    //  THEME (light / dark) + UI FACTORIES
    // =====================================================================
    static boolean dark = false;
    static Color BG, CARD, ALT, TEXT, MUTED, LINE, FIELD;
    static final Color PRIMARY = new Color(99, 102, 241);
    static final Color PRIMARY_D = new Color(67, 56, 202);
    static final Color ACCENT = new Color(14, 165, 233);
    static final Color SUCCESS = new Color(16, 185, 129);
    static final Color DANGER = new Color(239, 68, 68);
    static final Color WARN = new Color(245, 158, 11);
    static final Color GREY_BTN = new Color(100, 116, 139);

    static void applyTheme(boolean d) {
        dark = d;
        if (d) {
            BG = new Color(15, 23, 42); CARD = new Color(30, 41, 59); ALT = new Color(36, 48, 70);
            TEXT = new Color(226, 232, 240); MUTED = new Color(148, 163, 184); LINE = new Color(51, 65, 85);
            FIELD = new Color(15, 23, 42);
        } else {
            BG = new Color(241, 245, 249); CARD = Color.WHITE; ALT = new Color(248, 250, 252);
            TEXT = new Color(30, 41, 59); MUTED = new Color(100, 116, 139); LINE = new Color(226, 232, 240);
            FIELD = Color.WHITE;
        }
        Object[][] kv = {
                {"Panel.background", BG}, {"OptionPane.background", BG}, {"OptionPane.messageForeground", TEXT},
                {"Label.foreground", TEXT}, {"CheckBox.foreground", TEXT}, {"RadioButton.foreground", TEXT},
                {"CheckBox.background", CARD}, {"RadioButton.background", CARD},
                {"TextField.background", FIELD}, {"TextField.foreground", TEXT}, {"TextField.caretForeground", TEXT},
                {"TextField.inactiveBackground", FIELD}, {"TextField.inactiveForeground", TEXT},
                {"PasswordField.background", FIELD}, {"PasswordField.foreground", TEXT},
                {"PasswordField.caretForeground", TEXT},
                {"TextArea.background", FIELD}, {"TextArea.foreground", TEXT},
                {"ComboBox.background", FIELD}, {"ComboBox.foreground", TEXT},
                {"ComboBox.selectionBackground", PRIMARY}, {"ComboBox.selectionForeground", Color.WHITE},
                {"ComboBox.disabledBackground", FIELD}, {"ComboBox.disabledForeground", MUTED},
                {"List.background", FIELD}, {"List.foreground", TEXT},
                {"List.selectionBackground", PRIMARY}, {"List.selectionForeground", Color.WHITE},
                {"Table.background", CARD}, {"Table.foreground", TEXT},
                {"ScrollPane.background", CARD}, {"Viewport.background", CARD},
                {"ScrollBar.background", BG}, {"ScrollBar.track", BG}, {"ScrollBar.thumb", LINE},
                {"ToolTip.background", CARD}, {"ToolTip.foreground", TEXT},
                {"FileChooser.background", BG}
        };
        for (Object[] e : kv) UIManager.put(e[0], e[1]);
    }

    static Font f(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    static Color lighten(Color c, int n) {
        return new Color(Math.min(255, c.getRed() + n), Math.min(255, c.getGreen() + n),
                Math.min(255, c.getBlue() + n), c.getAlpha());
    }

    static Color mix(Color a, Color b, float t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(f(style, size));
        l.setForeground(color);
        return l;
    }

    static JLabel clabel(String text, int style, int size, Color color) {
        JLabel l = label(text, style, size, color);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }

    static JPanel box(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    static JCheckBox check(String text) {
        JCheckBox c = new JCheckBox(text);
        c.setOpaque(false);
        c.setForeground(TEXT);
        c.setFont(f(Font.PLAIN, 13));
        c.setFocusPainted(false);
        return c;
    }

    static JRadioButton radio(String text, boolean sel) {
        JRadioButton r = new JRadioButton(text, sel);
        r.setOpaque(false);
        r.setForeground(TEXT);
        r.setFont(f(Font.PLAIN, 13));
        r.setFocusPainted(false);
        return r;
    }

    static void styleField(JTextField t) {
        t.setFont(f(Font.PLAIN, 14));
        t.setForeground(TEXT);
        t.setBackground(FIELD);
        t.setCaretColor(TEXT);
        final javax.swing.border.Border idle = BorderFactory.createCompoundBorder(
                new LineBorder(LINE, 1, true), new EmptyBorder(6, 10, 6, 10));
        final javax.swing.border.Border focus = BorderFactory.createCompoundBorder(
                new LineBorder(PRIMARY, 2, true), new EmptyBorder(5, 9, 5, 9));
        t.setBorder(idle);
        t.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                t.setBorder(focus);
            }

            @Override
            public void focusLost(FocusEvent e) {
                t.setBorder(idle);
            }
        });
    }

    static JTextField field(int cols) {
        JTextField t = new JTextField(cols);
        styleField(t);
        return t;
    }

    static JPasswordField pass(int cols) {
        JPasswordField t = new JPasswordField(cols);
        styleField(t);
        return t;
    }

    static void styleCombo(JComboBox<?> c, int width) {
        c.setFont(f(Font.PLAIN, 13));
        c.setBackground(FIELD);
        c.setForeground(TEXT);
        c.setPreferredSize(new Dimension(width, 34));
    }

    static void addRow(JPanel p, int y, String text, JComponent comp) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = y; g.anchor = GridBagConstraints.WEST; g.insets = new Insets(4, 4, 4, 10);
        p.add(label(text, Font.PLAIN, 13, MUTED), g);
        g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; g.insets = new Insets(4, 0, 4, 4);
        p.add(comp, g);
    }

    static DocumentListener docListener(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    static DefaultTableModel readOnlyModel(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
    }

    static JTable table(DefaultTableModel m) {
        JTable t = new JTable(m);
        t.setRowHeight(32);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFillsViewportHeight(true);
        t.setBackground(CARD);
        t.setForeground(TEXT);
        t.setDefaultRenderer(Object.class, new StyledRenderer());
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setPreferredSize(new Dimension(0, 36));
        t.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean fo, int r, int c) {
                super.getTableCellRendererComponent(tb, v, s, fo, r, c);
                setOpaque(true);
                setBackground(PRIMARY_D);
                setForeground(Color.WHITE);
                setFont(f(Font.BOLD, 13));
                setBorder(new EmptyBorder(0, 10, 0, 10));
                return this;
            }
        });
        return t;
    }

    static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(new LineBorder(LINE, 1, false));
        sp.getViewport().setBackground(CARD);
        return sp;
    }

    static void digitsOnly(JTextField t, int max) {
        formatDigits(t, max, 0, ' ');
    }

    /** Installs auto-formatting: keeps only digits, limits length, inserts a separator every `group` digits. */
    static void formatDigits(JTextField t, int maxDigits, int group, char sep) {
        ((AbstractDocument) t.getDocument()).setDocumentFilter(new DigitFormatFilter(maxDigits, group, sep));
    }

    static class DigitFormatFilter extends DocumentFilter {
        private final int maxDigits, group;
        private final char sep;

        DigitFormatFilter(int maxDigits, int group, char sep) {
            this.maxDigits = maxDigits; this.group = group; this.sep = sep;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, javax.swing.text.AttributeSet a)
                throws BadLocationException {
            apply(fb, offset, 0, text);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet a)
                throws BadLocationException {
            apply(fb, offset, length, text);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            apply(fb, offset, length, "");
        }

        private void apply(FilterBypass fb, int offset, int length, String text) throws BadLocationException {
            Document doc = fb.getDocument();
            String cur = doc.getText(0, doc.getLength());
            String merged = cur.substring(0, offset) + (text == null ? "" : text) + cur.substring(offset + length);
            StringBuilder out = new StringBuilder();
            int count = 0;
            for (char ch : merged.toCharArray()) {
                if (!Character.isDigit(ch) || count >= maxDigits) continue;
                if (group > 0 && count > 0 && count % group == 0) out.append(sep);
                out.append(ch);
                count++;
            }
            fb.replace(0, doc.getLength(), out.toString(), null);
        }
    }

    // =====================================================================
    //  CUSTOM-PAINTED COMPONENTS (AWT Graphics2D)
    // =====================================================================
    static class RoundButton extends JButton {
        private final Color base;
        private final int arc;
        private boolean hover, press;

        RoundButton(String text, Color base) {
            this(text, base, 14);
        }

        RoundButton(String text, Color base, int arc) {
            super(text);
            this.base = base;
            this.arc = arc;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(f(Font.BOLD, 13));
            setBorder(new EmptyBorder(8, 16, 8, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    press = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    press = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    press = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            Color c = base;
            if (!isEnabled()) c = alpha(base, 80);
            else if (press) c = base.darker();
            else if (hover) c = lighten(base, 22);
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class NavButton extends JToggleButton {
        private boolean hover;

        NavButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(f(Font.BOLD, 14));
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(new EmptyBorder(12, 18, 12, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            if (isSelected()) {
                g2.setColor(alpha(Color.WHITE, 48));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, 10, 4, getHeight() - 20, 4, 4);
            } else if (hover) {
                g2.setColor(alpha(Color.WHITE, 22));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class Card extends JPanel {
        Card(LayoutManager lm) {
            super(lm);
            setOpaque(false);
            setBorder(new EmptyBorder(14, 16, 20, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(new Color(0, 0, 0, dark ? 60 : 16));
            g2.fillRoundRect(1, 3, getWidth() - 2, getHeight() - 6, 20, 20);
            g2.setColor(CARD);
            g2.fillRoundRect(0, 0, getWidth() - 2, getHeight() - 6, 20, 20);
            g2.setColor(LINE);
            g2.drawRoundRect(0, 0, getWidth() - 2, getHeight() - 6, 20, 20);
            g2.dispose();
        }
    }

    static class GradientPanel extends JPanel {
        private final Color c1, c2;
        private final boolean vertical;

        GradientPanel(Color c1, Color c2, boolean vertical) {
            this.c1 = c1; this.c2 = c2; this.vertical = vertical;
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setPaint(vertical ? new GradientPaint(0, 0, c1, 0, getHeight(), c2)
                    : new GradientPaint(0, 0, c1, getWidth(), 0, c2));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    static class SeatButton extends JButton {
        int state;           // 0 free, 1 selected, 2 booked
        boolean premium;
        private boolean hover;

        SeatButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(f(Font.BOLD, 11));
            setPreferredSize(new Dimension(42, 32));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            Color free = dark ? new Color(71, 85, 105) : new Color(203, 213, 225);
            Color fill = state == 2 ? DANGER : state == 1 ? SUCCESS : (hover ? mix(free, PRIMARY, 0.45f) : free);
            g2.setColor(fill);
            g2.fillRoundRect(1, 1, w - 2, h - 2, 12, 12);
            if (premium && state == 0) {
                g2.setColor(WARN);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 12, 12);
            }
            g2.setFont(getFont());
            g2.setColor(state == 0 ? TEXT : Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            String t = getText();
            g2.drawString(t, (w - fm.stringWidth(t)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    static class StyledRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, false, row, col);
            String s = v == null ? "" : v.toString();
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setFont(f(Font.PLAIN, 13));
            setBackground(sel ? PRIMARY : (row % 2 == 0 ? CARD : ALT));
            setForeground(sel ? Color.WHITE : TEXT);
            if (!sel) {
                String name = t.getColumnName(col);
                if (s.equals("CONFIRMED")) {
                    setForeground(SUCCESS);
                    setFont(f(Font.BOLD, 13));
                } else if (s.equals("CANCELLED")) {
                    setForeground(DANGER);
                    setFont(f(Font.BOLD, 13));
                } else if (name.equals("Seats Left")) {
                    try {
                        int n = Integer.parseInt(s.trim());
                        setForeground(n <= 3 ? DANGER : n <= 10 ? WARN : SUCCESS);
                        setFont(f(Font.BOLD, 13));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return this;
        }
    }

    static class BarChart extends JPanel {
        private Map<String, Double> data = new LinkedHashMap<>();

        BarChart() {
            setOpaque(false);
            setPreferredSize(new Dimension(320, 220));
        }

        void setData(Map<String, Double> d) {
            data = d;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            if (data.isEmpty()) {
                g2.setColor(MUTED);
                g2.setFont(f(Font.PLAIN, 14));
                String s = "No bookings yet - book your first flight!";
                g2.drawString(s, (w - g2.getFontMetrics().stringWidth(s)) / 2, h / 2);
                g2.dispose();
                return;
            }
            double max = 1;
            for (double v : data.values()) max = Math.max(max, v);
            int n = data.size(), left = 10, bottom = 30, top = 28;
            int slot = (w - left * 2) / n;
            int barW = Math.min(56, slot - 16);
            int i = 0;
            for (Map.Entry<String, Double> e : data.entrySet()) {
                int bh = (int) ((h - bottom - top) * (e.getValue() / max));
                int x = left + i * slot + (slot - barW) / 2;
                int y = h - bottom - bh;
                g2.setPaint(new GradientPaint(0, y, ACCENT, 0, y + bh, PRIMARY));
                g2.fillRoundRect(x, y, barW, bh, 10, 10);
                g2.setColor(TEXT);
                g2.setFont(f(Font.BOLD, 12));
                String v = String.format(Locale.ENGLISH, "%.0f", e.getValue());
                g2.drawString(v, x + (barW - g2.getFontMetrics().stringWidth(v)) / 2, y - 6);
                g2.setColor(MUTED);
                g2.setFont(f(Font.PLAIN, 11));
                String k = e.getKey();
                g2.drawString(k, x + (barW - g2.getFontMetrics().stringWidth(k)) / 2, h - 10);
                i++;
            }
            g2.setColor(LINE);
            g2.drawLine(left, h - bottom, w - left, h - bottom);
            g2.dispose();
        }
    }

    static void drawPlane(Graphics2D g, double x, double y, double scale, double angle, Color c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x, y);
        g2.rotate(angle);
        g2.scale(scale, scale);
        g2.setColor(c);
        g2.fill(new Ellipse2D.Double(-30, -6, 60, 12));
        Path2D.Double wing = new Path2D.Double();
        wing.moveTo(-6, 0); wing.lineTo(-20, -28); wing.lineTo(-10, -28); wing.lineTo(12, 0); wing.closePath();
        g2.fill(wing);
        Path2D.Double wing2 = new Path2D.Double();
        wing2.moveTo(-6, 0); wing2.lineTo(-20, 28); wing2.lineTo(-10, 28); wing2.lineTo(12, 0); wing2.closePath();
        g2.fill(wing2);
        Path2D.Double tail = new Path2D.Double();
        tail.moveTo(-26, 0); tail.lineTo(-34, -12); tail.lineTo(-28, -12); tail.lineTo(-18, 0); tail.closePath();
        g2.fill(tail);
        Path2D.Double tail2 = new Path2D.Double();
        tail2.moveTo(-26, 0); tail2.lineTo(-34, 12); tail2.lineTo(-28, 12); tail2.lineTo(-18, 0); tail2.closePath();
        g2.fill(tail2);
        g2.dispose();
    }

    static void drawRight(Graphics2D g, String s, int xRight, int y) {
        g.drawString(s, xRight - g.getFontMetrics().stringWidth(s), y);
    }

    static void drawCentered(Graphics2D g, String s, int cx, int y) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    /** Animated hero panel used on the login screen. */
    static class HeroPanel extends GradientPanel {
        private double t = 0;
        private final javax.swing.Timer timer;

        HeroPanel() {
            super(new Color(67, 56, 202), new Color(14, 165, 233), true);
            timer = new javax.swing.Timer(30, e -> {
                t += 0.004;
                if (t > 1.0) t -= 1.0;
                repaint();
            });
            timer.start();
        }

        void stop() {
            timer.stop();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(255, 255, 255, 25));
            g2.fillOval(-60, h - 200, 260, 260);
            g2.fillOval(w - 150, 40, 220, 220);
            g2.fillOval(w / 2, h / 2 - 60, 120, 120);

            double arc = h * 0.30, baseY = h * 0.50, span = w + 140;
            for (double s = 0; s < t; s += 0.015) {
                double px = -70 + s * span, py = baseY - arc * Math.sin(Math.PI * s);
                g2.setColor(new Color(255, 255, 255, (int) (40 + 120 * (s / Math.max(t, 0.01)))));
                g2.fill(new Ellipse2D.Double(px - 2, py - 2, 4, 4));
            }
            double x = -70 + t * span, y = baseY - arc * Math.sin(Math.PI * t);
            double ang = Math.atan2(-arc * Math.PI * Math.cos(Math.PI * t), span);
            drawPlane(g2, x, y, 1.1, ang, Color.WHITE);

            g2.setColor(Color.WHITE);
            g2.setFont(f(Font.BOLD, 40));
            g2.drawString("SkyWings", 36, h - 160);
            g2.setFont(f(Font.PLAIN, 16));
            g2.setColor(new Color(224, 231, 255));
            g2.drawString("Fly smarter. Book faster.", 38, h - 130);
            g2.setFont(f(Font.PLAIN, 13));
            g2.drawString("- Pick any date with the calendar", 38, h - 92);
            g2.drawString("- Smart seat map, meals and baggage", 38, h - 70);
            g2.drawString("- Earn loyalty points on every trip", 38, h - 48);
            g2.dispose();
        }
    }

    /** Credit-card preview that updates while the user types. */
    static class CardPreview extends JPanel {
        private String number = "", holder = "", expiry = "";

        CardPreview() {
            setOpaque(false);
            setPreferredSize(new Dimension(330, 190));
        }

        void update(String n, String h, String e) {
            number = n; holder = h; expiry = e;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth() - 4, h = getHeight() - 4;
            g2.setPaint(new GradientPaint(0, 0, new Color(30, 27, 75), w, h, new Color(99, 102, 241)));
            g2.fillRoundRect(0, 0, w, h, 24, 24);
            g2.setColor(new Color(255, 255, 255, 30));
            g2.fillOval(w - 120, -60, 200, 200);
            g2.setColor(new Color(250, 204, 21));
            g2.fillRoundRect(24, 50, 44, 32, 8, 8);
            g2.setColor(new Color(161, 98, 7));
            g2.drawLine(24, 66, 68, 66);
            g2.drawLine(46, 50, 46, 82);

            String digits = number.replace(" ", "");
            g2.setColor(Color.WHITE);
            g2.setFont(f(Font.BOLD, 13));
            drawRight(g2, brand(digits), w - 22, 34);

            StringBuilder disp = new StringBuilder();
            int di = 0;
            for (int i = 0; i < 19; i++) {
                if (i % 5 == 4) disp.append(' ');
                else disp.append(di < digits.length() ? digits.charAt(di++) : '*');
            }
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
            g2.drawString(disp.toString(), 24, 124);
            g2.setFont(f(Font.PLAIN, 10));
            g2.setColor(new Color(199, 210, 254));
            g2.drawString("CARD HOLDER", 24, 148);
            g2.drawString("VALID THRU", w - 100, 148);
            g2.setColor(Color.WHITE);
            g2.setFont(f(Font.BOLD, 14));
            String hn = holder.trim().isEmpty() ? "YOUR NAME" : holder.trim().toUpperCase();
            g2.drawString(hn.length() > 20 ? hn.substring(0, 20) : hn, 24, 168);
            g2.drawString(expiry.isEmpty() ? "MM/YY" : expiry, w - 100, 168);
            g2.dispose();
        }
    }

    /** Boarding pass drawn with Java2D (includes a barcode generated from the PNR). */
    static class BoardingPass extends JPanel {
        static final int W = 700, H = 270, MAIN = 520;
        private final Booking b;

        BoardingPass(Booking b) {
            this.b = b;
            setOpaque(false);
            setPreferredSize(new Dimension(W + 20, H + 20));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.translate(10, 10);
            draw(g2);
            g2.dispose();
        }

        BufferedImage toImage() {
            BufferedImage img = new BufferedImage(W + 20, H + 20, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = aa(img.createGraphics());
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W + 20, H + 20);
            g.translate(10, 10);
            draw(g);
            g.dispose();
            return img;
        }

        private void field(Graphics2D g2, String label, String value, int x, int y) {
            g2.setFont(f(Font.BOLD, 10));
            g2.setColor(new Color(100, 116, 139));
            g2.drawString(label, x, y - 17);
            g2.setFont(f(Font.BOLD, 15));
            g2.setColor(new Color(30, 41, 59));
            g2.drawString(value, x, y);
        }

        void draw(Graphics2D g2) {
            Flight fl = b.flight;
            Color dk = new Color(30, 41, 59), mu = new Color(100, 116, 139);
            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, W, H, 28, 28);
            g2.setColor(new Color(0, 0, 0, 35));
            g2.fill(new RoundRectangle2D.Float(3, 5, W, H, 28, 28));
            g2.setColor(Color.WHITE);
            g2.fill(shape);
            Shape old = g2.getClip();
            g2.clip(shape);
            g2.setPaint(new GradientPaint(0, 0, PRIMARY_D, W, 0, ACCENT));
            g2.fillRect(0, 0, W, 62);
            g2.setColor(new Color(238, 242, 255));
            g2.fillRect(MAIN, 62, W - MAIN, H - 62);
            g2.setClip(old);

            g2.setColor(Color.WHITE);
            g2.setFont(f(Font.BOLD, 22));
            g2.drawString("SKYWINGS AIRLINES", 26, 39);
            g2.setFont(f(Font.BOLD, 13));
            drawRight(g2, "BOARDING PASS", MAIN - 24, 38);
            g2.setFont(f(Font.BOLD, 11));
            g2.drawString("PASSENGER COPY", MAIN + 26, 38);

            g2.setColor(dk);
            g2.setFont(f(Font.BOLD, 46));
            g2.drawString(code(fl.from), 26, 128);
            drawRight(g2, code(fl.to), MAIN - 26, 128);
            g2.setColor(mu);
            g2.setFont(f(Font.PLAIN, 13));
            g2.drawString(fl.from, 28, 148);
            drawRight(g2, fl.to, MAIN - 28, 148);

            g2.setColor(new Color(165, 180, 252));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 6}, 0));
            g2.drawLine(150, 108, MAIN / 2 - 45, 108);
            g2.drawLine(MAIN / 2 + 45, 108, MAIN - 150, 108);
            g2.setStroke(new BasicStroke(1f));
            drawPlane(g2, MAIN / 2.0, 108, 0.8, 0, PRIMARY);
            g2.setColor(mu);
            g2.setFont(f(Font.PLAIN, 12));
            drawCentered(g2, fl.durationText(), MAIN / 2, 140);

            String name = b.passenger.toUpperCase();
            if (name.length() > 17) name = name.substring(0, 16) + ".";
            field(g2, "PASSENGER", name, 26, 190);
            field(g2, "FLIGHT", fl.id, 226, 190);
            field(g2, "SEAT", b.seat, 316, 190);
            field(g2, "CLASS", b.travelClass.toUpperCase(), 396, 190);
            String boards = LocalTime.parse(fl.depart).minusMinutes(45).toString();
            String gate = "G" + (Math.floorMod(b.pnr.hashCode(), 12) + 1);
            field(g2, "DATE", b.date.format(SHORT_FMT).toUpperCase(), 26, 240);
            field(g2, "BOARDS", boards, 160, 240);
            field(g2, "DEPARTS", fl.depart, 250, 240);
            field(g2, "ARRIVES", fl.arrive, 345, 240);
            field(g2, "GATE", gate, 440, 240);

            g2.setColor(new Color(165, 180, 252));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 5}, 0));
            g2.drawLine(MAIN, 62, MAIN, H);
            g2.setStroke(new BasicStroke(1f));

            g2.setColor(mu);
            g2.setFont(f(Font.BOLD, 10));
            g2.drawString("PNR", MAIN + 26, 92);
            g2.setColor(dk);
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 26));
            g2.drawString(b.pnr, MAIN + 26, 120);
            Random rnd = new Random(b.pnr.hashCode());
            int x = MAIN + 26;
            while (x < W - 26) {
                int bw = 1 + rnd.nextInt(3);
                g2.fillRect(x, 138, bw, 70);
                x += bw + 1 + rnd.nextInt(3);
            }
            g2.setFont(f(Font.BOLD, 12));
            g2.drawString(code(fl.from) + " > " + code(fl.to) + "   " + b.seat, MAIN + 26, 236);

            if ("CANCELLED".equals(b.status)) {
                Graphics2D s = (Graphics2D) g2.create();
                s.rotate(-0.35, W / 2.0, H / 2.0);
                s.setColor(new Color(239, 68, 68, 160));
                s.setFont(f(Font.BOLD, 70));
                drawCentered(s, "CANCELLED", W / 2, H / 2 + 24);
                s.setStroke(new BasicStroke(5f));
                s.drawRect(W / 2 - 210, H / 2 - 40, 420, 80);
                s.dispose();
            }
        }
    }

    // =====================================================================
    //  CALENDAR (date picker)
    // =====================================================================
    static class CalendarDialog extends JDialog {
        private LocalDate result;
        private YearMonth ym;
        private final LocalDate selected, min, max;
        private final JLabel title = clabel("", Font.BOLD, 17, TEXT);
        private final JPanel grid = box(new GridLayout(7, 7, 5, 5));

        private CalendarDialog(Window owner, LocalDate initial, LocalDate min, LocalDate max) {
            super(owner, "Select a date", Dialog.ModalityType.APPLICATION_MODAL);
            this.selected = initial;
            this.min = min;
            this.max = max;
            this.ym = YearMonth.from(initial);

            JPanel root = new JPanel(new BorderLayout(0, 10));
            root.setBackground(BG);
            root.setBorder(new EmptyBorder(16, 18, 16, 18));

            RoundButton py = new RoundButton("<<", PRIMARY, 10), pm = new RoundButton("<", PRIMARY, 10);
            RoundButton nm = new RoundButton(">", PRIMARY, 10), ny = new RoundButton(">>", PRIMARY, 10);
            py.addActionListener(e -> shift(-12));
            pm.addActionListener(e -> shift(-1));
            nm.addActionListener(e -> shift(1));
            ny.addActionListener(e -> shift(12));
            JPanel west = box(new FlowLayout(FlowLayout.LEFT, 4, 0));
            west.add(py);
            west.add(pm);
            JPanel east = box(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            east.add(nm);
            east.add(ny);
            JPanel header = box(new BorderLayout(8, 0));
            header.add(west, BorderLayout.WEST);
            header.add(title, BorderLayout.CENTER);
            header.add(east, BorderLayout.EAST);

            RoundButton firstDate = new RoundButton("26 Oct", ACCENT, 10);
            RoundButton lastDate = new RoundButton("26 Nov", ACCENT, 10);
            RoundButton cancel = new RoundButton("Cancel", GREY_BTN, 10);
            firstDate.setToolTipText("Select the first available booking date");
            lastDate.setToolTipText("Select the last available booking date");
            firstDate.addActionListener(e -> pick(BOOKING_START));
            lastDate.addActionListener(e -> pick(BOOKING_END));
            cancel.addActionListener(e -> dispose());
            JPanel quick = box(new FlowLayout(FlowLayout.CENTER, 8, 0));
            quick.add(firstDate);
            quick.add(lastDate);
            quick.add(cancel);

            root.add(header, BorderLayout.NORTH);
            root.add(grid, BorderLayout.CENTER);
            root.add(quick, BorderLayout.SOUTH);
            grid.setPreferredSize(new Dimension(340, 290));
            render();
            setContentPane(root);
            pack();
            setResizable(false);
            setLocationRelativeTo(owner);
        }

        private void shift(int months) {
            ym = ym.plusMonths(months);
            render();
        }

        private void pick(LocalDate d) {
            if (d.isBefore(min) || d.isAfter(max)) return;
            result = d;
            dispose();
        }

        private void render() {
            grid.removeAll();
            title.setText(ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear());
            for (String n : new String[]{"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"}) {
                grid.add(clabel(n, Font.BOLD, 12, MUTED));
            }
            int offset = ym.atDay(1).getDayOfWeek().getValue() % 7;
            for (int i = 0; i < offset; i++) grid.add(new JLabel());
            LocalDate now = BOOKING_START;
            for (int d = 1; d <= ym.lengthOfMonth(); d++) {
                final LocalDate dt = ym.atDay(d);
                Color base = dt.equals(selected) ? PRIMARY : dt.equals(now) ? ACCENT : LINE;
                RoundButton b = new RoundButton(String.valueOf(d), base, 12);
                b.setBorder(new EmptyBorder(6, 0, 6, 0));
                b.setForeground(dt.equals(selected) || dt.equals(now) ? Color.WHITE : TEXT);
                b.setFont(f(Font.BOLD, 13));
                b.setEnabled(!dt.isBefore(min) && !dt.isAfter(max));
                b.addActionListener(e -> pick(dt));
                grid.add(b);
            }
            grid.revalidate();
            grid.repaint();
        }

        static LocalDate choose(Component parent, LocalDate initial, LocalDate min, LocalDate max) {
            LocalDate init = initial.isBefore(min) ? min : initial.isAfter(max) ? max : initial;
            CalendarDialog d = new CalendarDialog(SwingUtilities.getWindowAncestor(parent), init, min, max);
            d.setVisible(true);
            return d.result;
        }
    }

    static class DatePicker extends JPanel {
        private LocalDate date;
        private final JTextField field = new JTextField(14);
        private Runnable onChange = () -> { };
        private final LocalDate min = BOOKING_START, max = BOOKING_END;

        DatePicker(LocalDate initial) {
            super(new BorderLayout(6, 0));
            setOpaque(false);
            this.date = isAllowedBookingDate(initial) ? initial : min;
            styleField(field);
            field.setEditable(false);
            field.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            RoundButton btn = new RoundButton("Calendar", ACCENT, 12);
            add(field, BorderLayout.CENTER);
            add(btn, BorderLayout.EAST);
            refresh();
            ActionListener open = e -> {
                LocalDate d = CalendarDialog.choose(this, date, min, max);
                if (d != null) {
                    date = d;
                    refresh();
                    onChange.run();
                }
            };
            btn.addActionListener(open);
            field.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    open.actionPerformed(null);
                }
            });
        }

        LocalDate getDate() {
            return date;
        }

        void setOnChange(Runnable r) {
            onChange = r;
        }

        private void refresh() {
            field.setText(date.format(DATE_FMT));
        }
    }

    // =====================================================================
    //  MAIN
    // =====================================================================
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        seed();
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.put("swing.boldMetal", Boolean.FALSE);
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            for (Object key : java.util.Collections.list(UIManager.getDefaults().keys())) {
                if (key instanceof String && ((String) key).endsWith(".font")) UIManager.put(key, f(Font.PLAIN, 13));
            }
            applyTheme(false);
            new LoginFrame().setVisible(true);
        });
    }

    // =====================================================================
    //  LOGIN SCREEN
    // =====================================================================
    static class LoginFrame extends JFrame {
        private final JTextField userField = field(18);
        private final JPasswordField passField = pass(18);
        private final JLabel msg = label(" ", Font.BOLD, 12, DANGER);
        private final HeroPanel hero = new HeroPanel();

        LoginFrame() {
            super("SkyWings Airlines - Sign in");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    hero.stop();
                }
            });

            JPanel root = new JPanel(new GridLayout(1, 2));
            setContentPane(root);
            root.add(hero);

            JPanel right = new JPanel(new GridBagLayout());
            right.setBackground(BG);
            root.add(right);

            final char defaultEcho = passField.getEchoChar();
            JCheckBox show = check("Show password");
            show.setOpaque(false);
            show.addItemListener(e -> passField.setEchoChar(show.isSelected() ? (char) 0 : defaultEcho));

            RoundButton login = new RoundButton("Sign in", PRIMARY, 14);
            login.setBorder(new EmptyBorder(11, 16, 11, 16));
            RoundButton create = new RoundButton("Create account", ACCENT, 14);
            create.setBorder(new EmptyBorder(11, 16, 11, 16));
            JButton clear = new RoundButton("Clear", GREY_BTN, 14);
            clear.setBorder(new EmptyBorder(11, 16, 11, 16));

            JPanel btns = box(new GridLayout(1, 2, 10, 0));
            btns.add(create);
            btns.add(clear);

            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            int y = 0;
            g.insets = new Insets(4, 46, 2, 46);
            g.gridy = y++;
            right.add(label("Welcome back", Font.BOLD, 30, TEXT), g);
            g.gridy = y++;
            right.add(label("Sign in to continue your journey", Font.PLAIN, 13, MUTED), g);
            g.insets = new Insets(24, 46, 2, 46);
            g.gridy = y++;
            right.add(label("Username", Font.BOLD, 12, MUTED), g);
            g.insets = new Insets(2, 46, 4, 46);
            g.gridy = y++;
            right.add(userField, g);
            g.insets = new Insets(10, 46, 2, 46);
            g.gridy = y++;
            right.add(label("Password", Font.BOLD, 12, MUTED), g);
            g.insets = new Insets(2, 46, 4, 46);
            g.gridy = y++;
            right.add(passField, g);
            g.gridy = y++;
            right.add(show, g);
            g.gridy = y++;
            right.add(msg, g);
            g.insets = new Insets(6, 46, 6, 46);
            g.gridy = y++;
            right.add(login, g);
            g.gridy = y++;
            right.add(btns, g);
            g.insets = new Insets(16, 46, 4, 46);
            g.gridy = y++;
            right.add(label("Demo:  user / user123   |   admin / admin123", Font.ITALIC, 12, MUTED), g);

            login.addActionListener(e -> doLogin());
            create.addActionListener(e -> register());
            clear.addActionListener(e -> {
                userField.setText("");
                passField.setText("");
                msg.setText(" ");
                userField.requestFocusInWindow();
            });
            getRootPane().setDefaultButton(login);

            setSize(900, 540);
            setResizable(false);
            setLocationRelativeTo(null);
        }

        private void doLogin() {
            String u = userField.getText().trim();
            String p = new String(passField.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                msg.setText("Please enter username and password.");
                shake();
                return;
            }
            if (users.containsKey(u) && users.get(u).equals(p)) {
                hero.stop();
                dispose();
                new MainFrame(u, "Dashboard").setVisible(true);
            } else {
                msg.setText("Invalid username or password.");
                passField.setText("");
                shake();
            }
        }

        private void shake() {
            final Point origin = getLocation();
            final int[] n = {0};
            javax.swing.Timer t = new javax.swing.Timer(25, null);
            t.addActionListener(e -> {
                n[0]++;
                int dx = (n[0] % 2 == 0 ? 1 : -1) * (12 - n[0]);
                setLocation(origin.x + dx, origin.y);
                if (n[0] >= 10) {
                    setLocation(origin);
                    t.stop();
                }
            });
            t.start();
        }

        private void register() {
            JTextField u = field(14);
            JPasswordField p1 = pass(14), p2 = pass(14);
            JPanel form = box(new GridBagLayout());
            addRow(form, 0, "Username:", u);
            addRow(form, 1, "Password:", p1);
            addRow(form, 2, "Confirm:", p2);
            int ok = JOptionPane.showConfirmDialog(this, form, "Create account",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            String name = u.getText().trim();
            String a = new String(p1.getPassword()), b = new String(p2.getPassword());
            String err = null;
            if (!name.matches("[A-Za-z0-9_]{3,15}")) err = "Username: 3-15 letters, digits or underscore.";
            else if (users.containsKey(name)) err = "That username is already taken.";
            else if (a.length() < 6) err = "Password must be at least 6 characters.";
            else if (!a.equals(b)) err = "Passwords do not match.";
            if (err != null) {
                JOptionPane.showMessageDialog(this, err, "Sign up", JOptionPane.WARNING_MESSAGE);
                return;
            }
            users.put(name, a);
            points.put(name, 100);
            userField.setText(name);
            passField.setText("");
            JOptionPane.showMessageDialog(this, "Account created! You received 100 welcome points.\nPlease sign in.",
                    "Sign up", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // =====================================================================
    //  MAIN APPLICATION WINDOW
    // =====================================================================
    static class MainFrame extends JFrame {
        private final String user;
        private final boolean admin;
        private final String bookingsKey;
        private final CardLayout cards = new CardLayout();
        private final JPanel pages = box(cards);
        private final Map<String, NavButton> navMap = new LinkedHashMap<>();
        private final JLabel pageTitle = label("", Font.BOLD, 24, TEXT);
        private final JLabel userLbl = label("", Font.BOLD, 13, TEXT);
        private final JLabel status = label(" Welcome aboard!", Font.PLAIN, 12, MUTED);
        private final JLabel clock = label("", Font.PLAIN, 12, MUTED);
        private javax.swing.Timer clockTimer;
        private String currentPage = "Dashboard";

        // dashboard
        private final JLabel greetLbl = label("", Font.BOLD, 26, TEXT);
        private final JLabel subLbl = label("", Font.PLAIN, 14, MUTED);
        private final JLabel stat1 = new JLabel(), stat2 = new JLabel(), stat3 = new JLabel(), stat4 = new JLabel();
        private final BarChart chart = new BarChart();
        private final JLabel tripLbl = label("", Font.PLAIN, 14, TEXT);

        // search + booking
        private final JComboBox<String> fromBox = new JComboBox<>();
        private final JComboBox<String> toBox = new JComboBox<>();
        private final DatePicker datePicker = new DatePicker(BOOKING_START);
        private final JLabel fareHint = label(" ", Font.ITALIC, 12, WARN);
        private DefaultTableModel flightModel;
        private JTable flightTable;
        private final List<Flight> shown = new ArrayList<>();
        private final SeatButton[][] seatBtns = new SeatButton[ROWS][COLS];
        private int selRow = -1, selCol = -1;
        private Flight current = null;
        private LocalDate searchDate = BOOKING_START;
        private String appliedPromo = null;

        private final JTextField nameF = field(14), ageF = field(14), emailF = field(14), promoF = field(10);
        private final JRadioButton male = radio("Male", true), female = radio("Female", false),
                other = radio("Other", false);
        private final JComboBox<String> classBox = new JComboBox<>(new String[]{"Economy", "Business"});
        private final JComboBox<String> mealBox = new JComboBox<>(MEALS);
        private final JCheckBox reserveFood = check("Reserve flight food");
        private final JComboBox<String> bagBox = new JComboBox<>(BAGS);
        private final JCheckBox insureChk = check("Travel insurance (+Rs. 199)");
        private final JCheckBox usePts = check("Use loyalty points");
        private final JLabel promoMsg = label(" ", Font.PLAIN, 12, MUTED);
        private final JLabel summaryLbl = new JLabel();
        private final JLabel totalLbl = label("Total: -", Font.BOLD, 22, PRIMARY);

        // bookings
        private DefaultTableModel bookModel;
        private JTable bookTable;
        private TableRowSorter<DefaultTableModel> bookSorter;
        private final List<Booking> shownBookings = new ArrayList<>();
        private final JTextField searchBook = field(20);

        // admin
        private DefaultTableModel adminModel;
        private JTable adminTable;

        MainFrame(String user, String startPage) {
            super("SkyWings Airlines - Flight Reservation System");
            this.user = user;
            this.admin = "admin".equals(user);
            this.bookingsKey = admin ? "All Bookings" : "My Bookings";

            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    exitApp();
                }
            });

            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(BG);
            setContentPane(root);

            pages.add(buildDashboard(), "Dashboard");
            pages.add(buildBookPage(), "Book Flight");
            pages.add(buildBookingsPage(), bookingsKey);
            if (admin) pages.add(buildAdminPage(), "Manage Flights");

            root.add(buildSidebar(), BorderLayout.WEST);
            JPanel main = new JPanel(new BorderLayout());
            main.setBackground(BG);
            main.add(buildTopBar(), BorderLayout.NORTH);
            main.add(pages, BorderLayout.CENTER);
            JPanel bar = new JPanel(new BorderLayout());
            bar.setBackground(BG);
            bar.setBorder(new EmptyBorder(4, 14, 6, 18));
            bar.add(status, BorderLayout.WEST);
            bar.add(clock, BorderLayout.EAST);
            main.add(bar, BorderLayout.SOUTH);
            root.add(main, BorderLayout.CENTER);

            populateCombos();
            search();
            refreshBookings();
            updateTopBar();
            bindKeys();

            clockTimer = new javax.swing.Timer(1000, e -> clock.setText(
                    java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm:ss", Locale.ENGLISH))));
            clockTimer.setInitialDelay(0);
            clockTimer.start();

            showPage(navMap.containsKey(startPage) ? startPage : "Dashboard");

            Dimension sc = Toolkit.getDefaultToolkit().getScreenSize();
            setSize(Math.min(1340, sc.width - 30), Math.min(860, sc.height - 60));
            setLocationRelativeTo(null);
        }

        @Override
        public void dispose() {
            if (clockTimer != null) clockTimer.stop();
            super.dispose();
        }

        // ---------- frame chrome ----------
        private JPanel buildSidebar() {
            GradientPanel side = new GradientPanel(new Color(30, 27, 75), new Color(49, 46, 129), true);
            side.setLayout(new BorderLayout());
            side.setPreferredSize(new Dimension(230, 0));

            JPanel logo = box(new GridLayout(2, 1));
            logo.setBorder(new EmptyBorder(26, 22, 20, 20));
            logo.add(label("SkyWings", Font.BOLD, 26, Color.WHITE));
            logo.add(label("A I R L I N E S", Font.PLAIN, 11, new Color(199, 210, 254)));

            JPanel nav = box(new GridLayout(0, 1, 0, 6));
            nav.setBorder(new EmptyBorder(6, 14, 6, 14));
            ButtonGroup bg = new ButtonGroup();
            List<String> keys = new ArrayList<>();
            keys.add("Dashboard");
            keys.add("Book Flight");
            keys.add(bookingsKey);
            if (admin) keys.add("Manage Flights");
            for (String k : keys) {
                NavButton nb = new NavButton(k);
                bg.add(nb);
                nav.add(nb);
                navMap.put(k, nb);
                nb.addActionListener(e -> showPage(k));
            }
            JPanel top = box(new BorderLayout());
            top.add(logo, BorderLayout.NORTH);
            top.add(nav, BorderLayout.CENTER);

            RoundButton theme = new RoundButton(dark ? "Light mode" : "Dark mode", alpha(Color.WHITE, 40), 16);
            RoundButton logout = new RoundButton("Logout", alpha(DANGER, 200), 16);
            RoundButton exit = new RoundButton("Exit", alpha(Color.WHITE, 40), 16);
            theme.addActionListener(e -> toggleTheme());
            logout.addActionListener(e -> doLogout());
            exit.addActionListener(e -> exitApp());
            JPanel bottom = box(new GridLayout(0, 1, 0, 8));
            bottom.setBorder(new EmptyBorder(8, 14, 20, 14));
            bottom.add(theme);
            bottom.add(logout);
            bottom.add(exit);

            side.add(top, BorderLayout.NORTH);
            side.add(bottom, BorderLayout.SOUTH);
            return side;
        }

        private JPanel buildTopBar() {
            JPanel p = new JPanel(new BorderLayout());
            p.setBackground(BG);
            p.setBorder(new EmptyBorder(18, 26, 4, 26));
            userLbl.setBorder(BorderFactory.createCompoundBorder(new LineBorder(LINE, 1, true),
                    new EmptyBorder(7, 16, 7, 16)));
            p.add(pageTitle, BorderLayout.WEST);
            p.add(userLbl, BorderLayout.EAST);
            return p;
        }

        private void updateTopBar() {
            int pts = points.getOrDefault(user, 0);
            userLbl.setText(admin ? user + "  |  Administrator"
                    : user + "  |  " + tierOf(pts) + " member  |  " + pts + " pts");
        }

        private void showPage(String name) {
            currentPage = name;
            cards.show(pages, name);
            pageTitle.setText(name);
            NavButton nb = navMap.get(name);
            if (nb != null) nb.setSelected(true);
            if ("Dashboard".equals(name)) refreshDashboard();
            if (bookingsKey.equals(name)) refreshBookings();
            setStatus("Opened " + name);
        }

        private void setStatus(String s) {
            status.setText(" " + s);
        }

        private void toggleTheme() {
            String page = currentPage;
            applyTheme(!dark);
            dispose();
            new MainFrame(user, page).setVisible(true);
        }

        private void doLogout() {
            if (JOptionPane.showConfirmDialog(this, "Logout now?", "Logout", JOptionPane.YES_NO_OPTION)
                    == JOptionPane.YES_OPTION) {
                dispose();
                new LoginFrame().setVisible(true);
            }
        }

        private void exitApp() {
            if (JOptionPane.showConfirmDialog(this, "Are you sure you want to exit?", "Exit",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        }

        private void bind(String name, int key, Runnable r) {
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK), name);
            getRootPane().getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    r.run();
                }
            });
        }

        /** Keyboard shortcuts using Swing key bindings. */
        private void bindKeys() {
            bind("dash", KeyEvent.VK_1, () -> showPage("Dashboard"));
            bind("book", KeyEvent.VK_2, () -> showPage("Book Flight"));
            bind("mine", KeyEvent.VK_3, () -> showPage(bookingsKey));
            bind("theme", KeyEvent.VK_D, this::toggleTheme);
            bind("logout", KeyEvent.VK_L, this::doLogout);
            bind("quit", KeyEvent.VK_Q, this::exitApp);
        }

        private void warn(String m) {
            JOptionPane.showMessageDialog(this, m, "Please check", JOptionPane.WARNING_MESSAGE);
        }

        // ---------- DASHBOARD ----------
        private List<Booking> visibleBookings() {
            List<Booking> l = new ArrayList<>();
            for (Booking b : bookings) if (admin || b.owner.equals(user)) l.add(b);
            return l;
        }

        private Card statCard(JLabel value, String caption, Color accent) {
            Card c = new Card(new BorderLayout(0, 4));
            value.setFont(f(Font.BOLD, 30));
            value.setForeground(accent);
            c.add(value, BorderLayout.NORTH);
            c.add(label(caption, Font.PLAIN, 13, MUTED), BorderLayout.CENTER);
            return c;
        }

        private JPanel buildDashboard() {
            JPanel p = box(new BorderLayout(0, 16));
            p.setBorder(new EmptyBorder(8, 26, 20, 26));

            JPanel head = box(new GridLayout(2, 1, 0, 2));
            head.add(greetLbl);
            head.add(subLbl);

            JPanel stats = box(new GridLayout(1, 4, 16, 0));
            stats.add(statCard(stat1, admin ? "Total bookings" : "My bookings", PRIMARY));
            stats.add(statCard(stat2, "Active (confirmed)", SUCCESS));
            stats.add(statCard(stat3, admin ? "Total revenue" : "Total spent", ACCENT));
            stats.add(statCard(stat4, admin ? "Flights operated" : "Loyalty points", WARN));

            Card chartCard = new Card(new BorderLayout(0, 8));
            chartCard.add(label("Bookings by route", Font.BOLD, 16, TEXT), BorderLayout.NORTH);
            chartCard.add(chart, BorderLayout.CENTER);

            Card tripCard = new Card(new BorderLayout(0, 10));
            tripCard.add(label("Your next trip", Font.BOLD, 16, TEXT), BorderLayout.NORTH);
            tripLbl.setVerticalAlignment(SwingConstants.TOP);
            tripCard.add(tripLbl, BorderLayout.CENTER);
            RoundButton go = new RoundButton("Book a flight", PRIMARY);
            RoundButton view = new RoundButton("View bookings", ACCENT);
            go.addActionListener(e -> showPage("Book Flight"));
            view.addActionListener(e -> showPage(bookingsKey));
            JPanel qb = box(new FlowLayout(FlowLayout.LEFT, 10, 0));
            qb.add(go);
            qb.add(view);
            tripCard.add(qb, BorderLayout.SOUTH);

            JPanel row = box(new GridLayout(1, 2, 16, 0));
            row.add(chartCard);
            row.add(tripCard);

            JPanel center = box(new BorderLayout(0, 16));
            center.add(stats, BorderLayout.NORTH);
            center.add(row, BorderLayout.CENTER);

            p.add(head, BorderLayout.NORTH);
            p.add(center, BorderLayout.CENTER);
            return p;
        }

        private void refreshDashboard() {
            int h = java.time.LocalTime.now().getHour();
            greetLbl.setText((h < 12 ? "Good morning, " : h < 17 ? "Good afternoon, " : "Good evening, ") + user);
            subLbl.setText("Here is a quick overview of " + (admin ? "all bookings." : "your trips and rewards."));
            List<Booking> mine = visibleBookings();
            int active = 0;
            double spent = 0;
            Map<String, Double> perRoute = new HashMap<>();
            Booking next = null;
            for (Booking b : mine) {
                if (!"CONFIRMED".equals(b.status)) continue;
                active++;
                spent += b.amount;
                perRoute.merge(code(b.flight.from) + "-" + code(b.flight.to), 1.0, Double::sum);
                if (!b.date.isBefore(BOOKING_START) && (next == null || b.date.isBefore(next.date))) next = b;
            }
            stat1.setText(String.valueOf(mine.size()));
            stat2.setText(String.valueOf(active));
            stat3.setText(String.format(Locale.ENGLISH, "Rs. %,.0f", spent));
            stat4.setText(admin ? String.valueOf(flights.size()) : String.valueOf(points.getOrDefault(user, 0)));

            List<Map.Entry<String, Double>> es = new ArrayList<>(perRoute.entrySet());
            es.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            Map<String, Double> top = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(6, es.size()); i++) top.put(es.get(i).getKey(), es.get(i).getValue());
            chart.setData(top);

            if (next == null) {
                tripLbl.setText("<html>No upcoming trips.<br>Pick a date on the calendar and book your next flight!</html>");
            } else {
                long days = ChronoUnit.DAYS.between(BOOKING_START, next.date);
                tripLbl.setText("<html><b style='font-size:16px'>" + next.flight.from + " to " + next.flight.to
                        + "</b><br>Flight " + next.flight.id + "  |  Seat " + next.seat
                        + "<br>" + next.date.format(DATE_FMT) + "  at  " + next.flight.depart
                        + "<br><br><b>" + (days == 0 ? "Departing today!" : "Departs in " + days + " day(s)")
                        + "</b><br>PNR: " + next.pnr + "</html>");
            }
        }

        // ---------- BOOK FLIGHT PAGE ----------
        private JComponent buildBookPage() {
            JPanel p = box(new BorderLayout(0, 14));
            p.setBorder(new EmptyBorder(8, 26, 20, 26));

            styleCombo(fromBox, 140);
            styleCombo(toBox, 140);
            RoundButton swap = new RoundButton("<->", GREY_BTN, 12);
            RoundButton searchBtn = new RoundButton("Search flights", PRIMARY);
            RoundButton reset = new RoundButton("Reset", WARN);
            Card sc = new Card(new FlowLayout(FlowLayout.LEFT, 10, 4));
            sc.add(label("From", Font.BOLD, 13, MUTED));
            sc.add(fromBox);
            sc.add(swap);
            sc.add(label("To", Font.BOLD, 13, MUTED));
            sc.add(toBox);
            sc.add(label("Date", Font.BOLD, 13, MUTED));
            sc.add(datePicker);
            sc.add(searchBtn);
            sc.add(reset);
            JPanel north = box(new BorderLayout(0, 0));
            north.add(sc, BorderLayout.CENTER);
            north.add(fareHint, BorderLayout.SOUTH);

            swap.addActionListener(e -> {
                Object a = fromBox.getSelectedItem();
                fromBox.setSelectedItem(toBox.getSelectedItem());
                toBox.setSelectedItem(a);
            });
            searchBtn.addActionListener(e -> search());
            datePicker.setOnChange(this::search);
            reset.addActionListener(e -> {
                fromBox.setSelectedIndex(0);
                toBox.setSelectedIndex(0);
                search();
            });

            flightModel = readOnlyModel("Flight", "From", "To", "Departs", "Arrives", "Duration", "Fare (Rs.)", "Seats Left");
            flightTable = table(flightModel);
            flightTable.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                int i = flightTable.getSelectedRow();
                if (i >= 0 && i < shown.size()) {
                    current = shown.get(i);
                    selRow = -1;
                    selCol = -1;
                    loadSeats();
                    updateFare();
                    setStatus("Selected " + current.id + ": " + current.from + " to " + current.to);
                }
            });
            Card fc = new Card(new BorderLayout(0, 8));
            fc.add(label("Available flights", Font.BOLD, 16, TEXT), BorderLayout.NORTH);
            fc.add(scroll(flightTable), BorderLayout.CENTER);
            fc.setPreferredSize(new Dimension(100, 250));

            JPanel cols = box(new GridBagLayout());
            GridBagConstraints g = new GridBagConstraints();
            g.fill = GridBagConstraints.BOTH;
            g.weighty = 1;
            g.insets = new Insets(0, 0, 0, 14);
            g.gridx = 0;
            g.weightx = 0.26;
            cols.add(buildSeatCard(), g);
            g.gridx = 1;
            g.weightx = 0.40;
            cols.add(buildPassengerCard(), g);
            g.gridx = 2;
            g.weightx = 0.34;
            g.insets = new Insets(0, 0, 0, 0);
            cols.add(buildFareCard(), g);

            JPanel center = box(new BorderLayout(0, 14));
            center.add(fc, BorderLayout.NORTH);
            center.add(cols, BorderLayout.CENTER);
            p.add(north, BorderLayout.NORTH);
            p.add(center, BorderLayout.CENTER);

            JScrollPane sp = new JScrollPane(p, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setBorder(null);
            sp.getViewport().setBackground(BG);
            sp.getVerticalScrollBar().setUnitIncrement(18);
            return sp;
        }

        private Card buildSeatCard() {
            Card c = new Card(new BorderLayout(0, 8));
            JPanel top = box(new BorderLayout());
            top.add(label("Choose your seat", Font.BOLD, 16, TEXT), BorderLayout.NORTH);
            JPanel legend = box(new FlowLayout(FlowLayout.LEFT, 10, 2));
            legend.add(legendItem(dark ? new Color(71, 85, 105) : new Color(203, 213, 225), "Free"));
            legend.add(legendItem(SUCCESS, "Yours"));
            legend.add(legendItem(DANGER, "Taken"));
            legend.add(legendItem(WARN, "Legroom"));
            top.add(legend, BorderLayout.CENTER);

            JPanel grid = box(new GridLayout(ROWS + 1, 8, 5, 5));
            grid.add(new JLabel(""));
            for (int col = 0; col < COLS; col++) {
                if (col == 3) grid.add(new JLabel(""));
                grid.add(clabel(String.valueOf(LETTERS.charAt(col)), Font.BOLD, 12, MUTED));
            }
            for (int r = 0; r < ROWS; r++) {
                grid.add(clabel(String.valueOf(r + 1), Font.BOLD, 12, MUTED));
                for (int col = 0; col < COLS; col++) {
                    if (col == 3) grid.add(new JLabel(""));
                    final int rr = r, cc = col;
                    SeatButton b = new SeatButton(seatName(r, col));
                    b.premium = r < 2;
                    b.addActionListener(e -> seatClicked(rr, cc));
                    b.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            setStatus(current == null ? "Select a flight first"
                                    : "Seat " + seatName(rr, cc) + ": " + (current.seats(searchDate)[rr][cc]
                                    ? "booked" : seatDesc(rr, cc) + " (+" + money(seatExtra(rr, cc)) + ")"));
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            setStatus("Ready");
                        }
                    });
                    seatBtns[r][col] = b;
                    grid.add(b);
                }
            }
            c.add(top, BorderLayout.NORTH);
            c.add(grid, BorderLayout.CENTER);
            return c;
        }

        private JPanel legendItem(Color col, String text) {
            JPanel p = box(new FlowLayout(FlowLayout.LEFT, 4, 0));
            JLabel box = new JLabel("    ");
            box.setOpaque(true);
            box.setBackground(col);
            p.add(box);
            p.add(label(text, Font.PLAIN, 12, MUTED));
            return p;
        }

        private Card buildPassengerCard() {
            Card c = new Card(new BorderLayout(0, 8));
            c.add(label("Passenger & extras", Font.BOLD, 16, TEXT), BorderLayout.NORTH);
            JPanel form = box(new GridBagLayout());
            styleCombo(classBox, 160);
            styleCombo(mealBox, 160);
            styleCombo(bagBox, 160);
            mealBox.setEnabled(false);
            mealBox.setToolTipText("Select the food you want during the flight");
            addRow(form, 0, "Full name", nameF);
            addRow(form, 1, "Age", ageF);
            digitsOnly(ageF, 3);
            ButtonGroup bg = new ButtonGroup();
            bg.add(male);
            bg.add(female);
            bg.add(other);
            JPanel gp = box(new FlowLayout(FlowLayout.LEFT, 4, 0));
            gp.add(male);
            gp.add(female);
            gp.add(other);
            addRow(form, 2, "Gender", gp);
            addRow(form, 3, "Email", emailF);
            addRow(form, 4, "Class", classBox);
            JPanel foodPanel = box(new FlowLayout(FlowLayout.LEFT, 5, 0));
            foodPanel.add(reserveFood);
            foodPanel.add(mealBox);
            addRow(form, 5, "Food preference", foodPanel);
            addRow(form, 6, "Baggage", bagBox);
            addRow(form, 7, "", insureChk);

            RoundButton apply = new RoundButton("Apply", ACCENT, 12);
            JPanel pr = box(new BorderLayout(6, 0));
            pr.add(promoF, BorderLayout.CENTER);
            pr.add(apply, BorderLayout.EAST);
            addRow(form, 8, "Promo code", pr);
            addRow(form, 9, "", promoMsg);
            addRow(form, 10, "", usePts);
            c.add(form, BorderLayout.CENTER);

            classBox.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) updateFare();
            });
            mealBox.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) updateFare();
            });
            reserveFood.addItemListener(e -> {
                boolean selected = e.getStateChange() == ItemEvent.SELECTED;
                mealBox.setEnabled(selected);
                if (!selected) mealBox.setSelectedIndex(0);
                updateFare();
            });
            bagBox.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) updateFare();
            });
            insureChk.addItemListener(e -> updateFare());
            usePts.addItemListener(e -> updateFare());
            apply.addActionListener(e -> applyPromo());
            promoF.addActionListener(e -> applyPromo());
            return c;
        }

        private Card buildFareCard() {
            Card c = new Card(new BorderLayout(0, 8));
            c.add(label("Fare summary", Font.BOLD, 16, TEXT), BorderLayout.NORTH);
            summaryLbl.setVerticalAlignment(SwingConstants.TOP);
            summaryLbl.setFont(f(Font.PLAIN, 13));
            summaryLbl.setForeground(TEXT);
            c.add(summaryLbl, BorderLayout.CENTER);

            RoundButton bookBtn = new RoundButton("Book & Pay", SUCCESS);
            bookBtn.setBorder(new EmptyBorder(11, 16, 11, 16));
            RoundButton clearBtn = new RoundButton("Clear form", GREY_BTN);
            clearBtn.setBorder(new EmptyBorder(11, 16, 11, 16));
            bookBtn.addActionListener(e -> book());
            clearBtn.addActionListener(e -> clearForm());
            JPanel south = box(new BorderLayout(0, 10));
            south.add(totalLbl, BorderLayout.NORTH);
            JPanel bp = box(new GridLayout(1, 2, 10, 0));
            bp.add(bookBtn);
            bp.add(clearBtn);
            south.add(bp, BorderLayout.CENTER);
            c.add(south, BorderLayout.SOUTH);
            updateFare();
            return c;
        }

        private void applyPromo() {
            String code = promoF.getText().trim().toUpperCase();
            if (code.isEmpty()) {
                appliedPromo = null;
                promoMsg.setText(" ");
            } else if (PROMOS.containsKey(code)) {
                appliedPromo = code;
                promoMsg.setForeground(SUCCESS);
                promoMsg.setText("Applied: " + PROMO_TEXT.get(code));
            } else {
                appliedPromo = null;
                promoMsg.setForeground(DANGER);
                promoMsg.setText("Invalid promo code (try SKY10)");
            }
            updateFare();
        }

        // ---------- search ----------
        private void populateCombos() {
            TreeSet<String> cities = new TreeSet<>();
            for (Flight fl : flights) {
                cities.add(fl.from);
                cities.add(fl.to);
            }
            for (JComboBox<String> box : java.util.Arrays.asList(fromBox, toBox)) {
                Object old = box.getSelectedItem();
                box.removeAllItems();
                box.addItem("Any");
                for (String s : cities) box.addItem(s);
                if (old != null) box.setSelectedItem(old);
            }
        }

        private void search() {
            String from = (String) fromBox.getSelectedItem();
            String to = (String) toBox.getSelectedItem();
            if (from != null && to != null && !"Any".equals(from) && from.equals(to)) {
                warn("Source and destination cannot be the same.");
                return;
            }
            searchDate = datePicker.getDate();
            if (!isAllowedBookingDate(searchDate)) {
                searchDate = BOOKING_START;
                warn("Please choose a date between " + bookingWindowText() + ".");
            }
            double dyn = dynFactor(searchDate);
            shown.clear();
            flightModel.setRowCount(0);
            for (Flight fl : flights) {
                if (from != null && !"Any".equals(from) && !fl.from.equals(from)) continue;
                if (to != null && !"Any".equals(to) && !fl.to.equals(to)) continue;
                shown.add(fl);
                flightModel.addRow(new Object[]{fl.id, fl.from, fl.to, fl.depart, fl.arrive, fl.durationText(),
                        String.format(Locale.ENGLISH, "%,.0f", fl.price * dyn), String.valueOf(fl.seatsLeft(searchDate))});
            }
            current = null;
            selRow = -1;
            selCol = -1;
            loadSeats();
            updateFare();
            fareHint.setText("   Booking window: " + bookingWindowText() + "  |  " + fareNote(searchDate));
            setStatus(shown.size() + " flight(s) on " + searchDate.format(DATE_FMT));
        }

        // ---------- seats ----------
        private void paintSeat(int r, int c) {
            SeatButton b = seatBtns[r][c];
            boolean booked = current != null && current.seats(searchDate)[r][c];
            b.state = booked ? 2 : (r == selRow && c == selCol) ? 1 : 0;
            b.setToolTipText(booked ? "Seat " + seatName(r, c) + " - booked"
                    : "Seat " + seatName(r, c) + " - " + seatDesc(r, c) + " (+" + money(seatExtra(r, c)) + ")");
            b.repaint();
        }

        private void loadSeats() {
            for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) paintSeat(r, c);
        }

        private void seatClicked(int r, int c) {
            if (current == null) {
                warn("Please select a flight from the table first.");
                return;
            }
            if (current.seats(searchDate)[r][c]) {
                warn("Seat " + seatName(r, c) + " is already booked.");
                return;
            }
            int pr = selRow, pc = selCol;
            if (pr == r && pc == c) {
                selRow = -1;
                selCol = -1;
            } else {
                selRow = r;
                selCol = c;
            }
            if (pr >= 0) paintSeat(pr, pc);
            paintSeat(r, c);
            updateFare();
        }

        // ---------- fare ----------
        private Fare computeFare() {
            if (current == null || selRow < 0) return null;
            Fare fr = new Fare();
            double mult = classBox.getSelectedIndex() == 1 ? 1.8 : 1.0;
            fr.base = current.price * mult * dynFactor(searchDate);
            fr.seat = seatExtra(selRow, selCol);
            fr.meal = reserveFood.isSelected() ? MEAL_PRICE[mealBox.getSelectedIndex()] : 0;
            fr.bag = BAG_PRICE[bagBox.getSelectedIndex()];
            fr.ins = insureChk.isSelected() ? 199 : 0;
            int have = points.getOrDefault(user, 0);
            fr.tierName = tierOf(have);
            fr.tier = fr.base * tierPercent(have) / 100.0;
            double afterTier = fr.base + fr.seat + fr.meal + fr.bag + fr.ins - fr.tier;
            if (appliedPromo != null) {
                double[] pr = PROMOS.get(appliedPromo);
                fr.promo = Math.min(Math.min(pr[2], afterTier * pr[0] / 100.0 + pr[1]), afterTier);
            }
            double afterPromo = afterTier - fr.promo;
            if (usePts.isSelected()) {
                fr.ptsUsed = Math.min(have, (int) (afterPromo * 0.20));
                fr.pts = fr.ptsUsed;
            }
            double taxable = afterPromo - fr.pts;
            fr.gst = taxable * 0.05;
            fr.total = taxable + fr.gst;
            return fr;
        }

        private void updateFare() {
            usePts.setText("Use loyalty points (available: " + points.getOrDefault(user, 0) + ")");
            Fare fr = computeFare();
            if (fr == null) {
                summaryLbl.setText("<html>Pick a date, choose a flight and then select a seat to see your fare.</html>");
                totalLbl.setText("Total: -");
            } else {
                String head = "<html><b>" + current.id + "</b>  " + code(current.from) + " to " + code(current.to)
                        + "  |  Seat <b>" + seatName(selRow, selCol) + "</b>  |  " + searchDate.format(SHORT_FMT) + "<br>";
                summaryLbl.setText(head + fr.html((String) classBox.getSelectedItem()).substring(6));
                totalLbl.setText("Total: " + money(fr.total));
            }
        }

        private void clearForm() {
            nameF.setText("");
            ageF.setText("");
            emailF.setText("");
            promoF.setText("");
            promoMsg.setText(" ");
            appliedPromo = null;
            male.setSelected(true);
            classBox.setSelectedIndex(0);
            reserveFood.setSelected(false);
            mealBox.setSelectedIndex(0);
            mealBox.setEnabled(false);
            bagBox.setSelectedIndex(0);
            insureChk.setSelected(false);
            usePts.setSelected(false);
            int pr = selRow, pc = selCol;
            selRow = -1;
            selCol = -1;
            if (pr >= 0) paintSeat(pr, pc);
            updateFare();
            setStatus("Form cleared.");
        }

        // ---------- booking ----------
        private void book() {
            if (!isAllowedBookingDate(searchDate)) {
                warn("Bookings are available only from " + bookingWindowText() + ".");
                return;
            }
            if (current == null) {
                warn("Please select a flight.");
                return;
            }
            if (selRow < 0) {
                warn("Please select a seat.");
                return;
            }
            String name = nameF.getText().trim();
            if (!name.matches("[A-Za-z ]{2,40}")) {
                warn("Enter a valid name (letters and spaces, 2-40 characters).");
                nameF.requestFocus();
                return;
            }
            int age;
            try {
                age = Integer.parseInt(ageF.getText().trim());
            } catch (NumberFormatException ex) {
                age = -1;
            }
            if (age < 1 || age > 120) {
                warn("Enter a valid age (1-120).");
                ageF.requestFocus();
                return;
            }
            String email = emailF.getText().trim();
            if (!email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
                warn("Enter a valid email address.");
                emailF.requestFocus();
                return;
            }
            Fare fr = computeFare();
            int ok = JOptionPane.showConfirmDialog(this,
                    String.format("Book seat %s on %s (%s to %s)%non %s%n%nTotal: %s%n%nProceed to payment?",
                            seatName(selRow, selCol), current.id, current.from, current.to,
                            searchDate.format(DATE_FMT), money(fr.total)),
                    "Confirm booking", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
            if (!payment(fr.total)) {
                setStatus("Payment cancelled.");
                return;
            }
            Booking b = new Booking();
            b.pnr = "SW" + (++pnrCounter);
            b.owner = user;
            b.flight = current;
            b.date = searchDate;
            b.passenger = name;
            b.age = age;
            b.gender = male.isSelected() ? "Male" : female.isSelected() ? "Female" : "Other";
            b.email = email;
            b.row = selRow;
            b.col = selCol;
            b.seat = seatName(selRow, selCol);
            b.travelClass = (String) classBox.getSelectedItem();
            b.meal = reserveFood.isSelected() ? (String) mealBox.getSelectedItem() : MEALS[0];
            b.bag = (String) bagBox.getSelectedItem();
            b.insurance = insureChk.isSelected();
            b.promo = appliedPromo;
            b.amount = fr.total;
            b.pointsUsed = fr.ptsUsed;
            b.pointsEarned = (int) (fr.total / 100);
            current.seats(searchDate)[selRow][selCol] = true;
            bookings.add(b);
            points.merge(user, b.pointsEarned - b.pointsUsed, Integer::sum);

            selRow = -1;
            selCol = -1;
            loadSeats();
            clearForm();
            refreshTables();
            refreshBookings();
            updateTopBar();
            setStatus("Booking confirmed! PNR " + b.pnr + "  (+" + b.pointsEarned + " points)");
            showTicket(b);
        }

        private boolean payment(double amount) {
            final boolean[] paid = {false};
            JDialog d = new JDialog(this, "Secure payment", true);
            JTextField holder = field(18), card = field(18), expiry = field(6);
            JPasswordField cvv = pass(4);
            formatDigits(card, 16, 4, ' ');      // 1234 5678 9012 3456
            formatDigits(expiry, 4, 2, '/');     // 12/28
            formatDigits(cvv, 3, 0, ' ');
            card.setToolTipText("16-digit card number");

            CardPreview prev = new CardPreview();
            Runnable upd = () -> prev.update(card.getText(), holder.getText(), expiry.getText());
            card.getDocument().addDocumentListener(docListener(upd));
            holder.getDocument().addDocumentListener(docListener(upd));
            expiry.getDocument().addDocumentListener(docListener(upd));
            JLabel brandLbl = label("Card type: -", Font.BOLD, 12, PRIMARY);
            card.getDocument().addDocumentListener(docListener(() ->
                    brandLbl.setText("Card type: " + brand(card.getText().replace(" ", "")))));

            JPanel form = box(new GridBagLayout());
            addRow(form, 0, "Card holder", holder);
            addRow(form, 1, "Card number", card);
            addRow(form, 2, "", brandLbl);
            addRow(form, 3, "Valid thru (MM/YY)", expiry);
            addRow(form, 4, "CVV", cvv);

            JLabel amt = label("Amount payable: " + money(amount), Font.BOLD, 17, PRIMARY);
            RoundButton pay = new RoundButton("Pay now", SUCCESS);
            RoundButton cancel = new RoundButton("Cancel", DANGER);
            JPanel bp = box(new FlowLayout(FlowLayout.CENTER, 12, 6));
            bp.add(pay);
            bp.add(cancel);

            pay.addActionListener(e -> {
                String digits = card.getText().replace(" ", "");
                String ex = expiry.getText().trim();
                String err = null;
                if (holder.getText().trim().length() < 2) err = "Enter the card holder name.";
                else if (digits.length() != 16) err = "Card number must be 16 digits.";
                else if (!Pattern.matches("(0[1-9]|1[0-2])/\\d{2}", ex)) err = "Expiry must be MM/YY.";
                else if (YearMonth.of(2000 + Integer.parseInt(ex.substring(3)), Integer.parseInt(ex.substring(0, 2)))
                        .isBefore(YearMonth.now())) err = "This card has expired.";
                else if (cvv.getPassword().length != 3) err = "CVV must be 3 digits.";
                if (err != null) {
                    JOptionPane.showMessageDialog(d, err, "Payment", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                pay.setEnabled(false);
                cancel.setEnabled(false);
                pay.setText("Processing...");
                javax.swing.Timer t = new javax.swing.Timer(1300, ev -> {
                    paid[0] = true;
                    d.dispose();
                });
                t.setRepeats(false);
                t.start();
            });
            cancel.addActionListener(e -> d.dispose());
            d.getRootPane().setDefaultButton(pay);

            JPanel root = new JPanel(new BorderLayout(0, 10));
            root.setBackground(BG);
            root.setBorder(new EmptyBorder(14, 18, 10, 18));
            JPanel top = box(new BorderLayout(0, 6));
            top.add(amt, BorderLayout.NORTH);
            top.add(prev, BorderLayout.CENTER);
            root.add(top, BorderLayout.NORTH);
            root.add(form, BorderLayout.CENTER);
            root.add(bp, BorderLayout.SOUTH);
            d.setContentPane(root);
            d.pack();
            d.setLocationRelativeTo(this);
            d.setVisible(true);
            return paid[0];
        }

        private void showTicket(Booking b) {
            BoardingPass pass = new BoardingPass(b);
            JDialog d = new JDialog(this, "Boarding pass - " + b.pnr, true);
            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(BG);
            root.setBorder(new EmptyBorder(10, 10, 6, 10));
            RoundButton img = new RoundButton("Save as image", PRIMARY);
            RoundButton txt = new RoundButton("Save as text", ACCENT);
            RoundButton close = new RoundButton("Close", GREY_BTN);
            img.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                fc.setSelectedFile(new File(b.pnr + ".png"));
                if (fc.showSaveDialog(d) == JFileChooser.APPROVE_OPTION) {
                    try {
                        ImageIO.write(pass.toImage(), "png", fc.getSelectedFile());
                        JOptionPane.showMessageDialog(d, "Saved to " + fc.getSelectedFile().getAbsolutePath());
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(d, "Could not save: " + ex.getMessage());
                    }
                }
            });
            txt.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                fc.setSelectedFile(new File(b.pnr + ".txt"));
                if (fc.showSaveDialog(d) == JFileChooser.APPROVE_OPTION) {
                    try (java.io.PrintWriter pw = new java.io.PrintWriter(fc.getSelectedFile())) {
                        pw.print(ticketText(b));
                        JOptionPane.showMessageDialog(d, "Saved to " + fc.getSelectedFile().getAbsolutePath());
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(d, "Could not save: " + ex.getMessage());
                    }
                }
            });
            close.addActionListener(e -> d.dispose());
            JPanel bp = box(new FlowLayout(FlowLayout.CENTER, 12, 8));
            bp.add(img);
            bp.add(txt);
            bp.add(close);
            root.add(pass, BorderLayout.CENTER);
            root.add(bp, BorderLayout.SOUTH);
            d.setContentPane(root);
            d.pack();
            d.setResizable(false);
            d.setLocationRelativeTo(this);
            d.setVisible(true);
        }

        private void refreshTables() {
            for (int i = 0; i < shown.size(); i++) {
                flightModel.setValueAt(String.valueOf(shown.get(i).seatsLeft(searchDate)), i, 7);
            }
            if (admin) refreshAdmin();
        }

        // ---------- BOOKINGS PAGE ----------
        private JPanel buildBookingsPage() {
            JPanel p = box(new BorderLayout(0, 12));
            p.setBorder(new EmptyBorder(8, 26, 20, 26));

            bookModel = readOnlyModel("PNR", "Flight", "Route", "Travel date", "Passenger", "Seat", "Amount (Rs.)", "Status", "Booked by");
            bookTable = table(bookModel);
            bookSorter = new TableRowSorter<>(bookModel);
            bookTable.setRowSorter(bookSorter);
            searchBook.getDocument().addDocumentListener(docListener(() -> {
                String t = searchBook.getText().trim();
                bookSorter.setRowFilter(t.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(t)));
            }));
            bookTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        Booking b = selectedBooking();
                        if (b != null) showTicket(b);
                    }
                }
            });

            JPanel top = box(new FlowLayout(FlowLayout.LEFT, 10, 0));
            top.add(label("Search (PNR, name, route):", Font.BOLD, 13, MUTED));
            top.add(searchBook);

            RoundButton view = new RoundButton("View boarding pass", PRIMARY);
            RoundButton resched = new RoundButton("Reschedule", WARN);
            RoundButton cancel = new RoundButton("Cancel booking", DANGER);
            view.addActionListener(e -> {
                Booking b = selectedBooking();
                if (b != null) showTicket(b);
            });
            resched.addActionListener(e -> reschedule());
            cancel.addActionListener(e -> cancelBooking());
            JPanel bottom = box(new FlowLayout(FlowLayout.CENTER, 12, 4));
            bottom.add(view);
            bottom.add(resched);
            bottom.add(cancel);

            Card c = new Card(new BorderLayout(0, 10));
            c.add(top, BorderLayout.NORTH);
            c.add(scroll(bookTable), BorderLayout.CENTER);
            c.add(bottom, BorderLayout.SOUTH);
            p.add(c, BorderLayout.CENTER);
            return p;
        }

        private void refreshBookings() {
            if (bookModel == null) return;
            bookModel.setRowCount(0);
            shownBookings.clear();
            for (Booking b : bookings) {
                if (!admin && !b.owner.equals(user)) continue;
                shownBookings.add(b);
                bookModel.addRow(new Object[]{b.pnr, b.flight.id, b.flight.from + " -> " + b.flight.to,
                        b.date.format(SHORT_FMT), b.passenger, b.seat,
                        String.format(Locale.ENGLISH, "%,.2f", b.amount), b.status, b.owner});
            }
        }

        private Booking selectedBooking() {
            int vr = bookTable.getSelectedRow();
            if (vr < 0) {
                warn("Please select a booking first.");
                return null;
            }
            return shownBookings.get(bookTable.convertRowIndexToModel(vr));
        }

        private void cancelBooking() {
            Booking b = selectedBooking();
            if (b == null) return;
            if ("CANCELLED".equals(b.status)) {
                warn("This booking is already cancelled.");
                return;
            }
            if (b.date.isBefore(BOOKING_START)) {
                warn("Past journeys cannot be cancelled.");
                return;
            }
            double refund = b.amount * 0.80;
            int ok = JOptionPane.showConfirmDialog(this,
                    String.format("Cancel booking %s?%nRefund (80%%): %s", b.pnr, money(refund)),
                    "Cancel booking", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) return;
            b.status = "CANCELLED";
            b.flight.seats(b.date)[b.row][b.col] = false;
            points.merge(b.owner, b.pointsUsed - b.pointsEarned, (a, c) -> Math.max(0, a + c));
            refreshBookings();
            refreshTables();
            if (current != null) loadSeats();
            updateTopBar();
            setStatus("Booking " + b.pnr + " cancelled. Refund " + money(refund));
        }

        private void reschedule() {
            Booking b = selectedBooking();
            if (b == null) return;
            if (!"CONFIRMED".equals(b.status)) {
                warn("Only confirmed bookings can be rescheduled.");
                return;
            }
            if (b.date.isBefore(BOOKING_START)) {
                warn("Past journeys cannot be rescheduled.");
                return;
            }
            LocalDate nd = CalendarDialog.choose(this, b.date, BOOKING_START, BOOKING_END);
            if (nd == null || nd.equals(b.date)) return;
            boolean[][] target = b.flight.seats(nd);
            if (target[b.row][b.col]) {
                warn("Seat " + b.seat + " is not free on " + nd.format(SHORT_FMT) + ".\nCancel and rebook with another seat.");
                return;
            }
            int ok = JOptionPane.showConfirmDialog(this,
                    "Move " + b.pnr + " to " + nd.format(DATE_FMT) + "?\nReschedule fee: Rs. 300.00",
                    "Reschedule", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
            b.flight.seats(b.date)[b.row][b.col] = false;
            target[b.row][b.col] = true;
            b.date = nd;
            b.amount += 300;
            refreshBookings();
            refreshTables();
            if (current != null) loadSeats();
            setStatus("Booking " + b.pnr + " moved to " + nd.format(SHORT_FMT));
        }

        // ---------- ADMIN PAGE ----------
        private JPanel buildAdminPage() {
            JPanel p = box(new BorderLayout(0, 12));
            p.setBorder(new EmptyBorder(8, 26, 20, 26));

            adminModel = readOnlyModel("Flight", "From", "To", "Departs", "Arrives", "Duration", "Base price (Rs.)", "Seats Left");
            adminTable = table(adminModel);
            refreshAdmin();

            JTextField id = field(5), from = field(8), to = field(8), dep = field(4), arr = field(4), price = field(6);
            dep.setToolTipText("HH:mm");
            arr.setToolTipText("HH:mm");
            JPanel form = box(new FlowLayout(FlowLayout.LEFT, 8, 4));
            form.add(label("ID", Font.BOLD, 12, MUTED));
            form.add(id);
            form.add(label("From", Font.BOLD, 12, MUTED));
            form.add(from);
            form.add(label("To", Font.BOLD, 12, MUTED));
            form.add(to);
            form.add(label("Dep (HH:mm)", Font.BOLD, 12, MUTED));
            form.add(dep);
            form.add(label("Arr (HH:mm)", Font.BOLD, 12, MUTED));
            form.add(arr);
            form.add(label("Price", Font.BOLD, 12, MUTED));
            form.add(price);
            RoundButton add = new RoundButton("Add flight", SUCCESS);
            form.add(add);
            Card top = new Card(new BorderLayout(0, 6));
            top.add(label("Add a daily flight with manual timings", Font.BOLD, 16, TEXT), BorderLayout.NORTH);
            top.add(form, BorderLayout.CENTER);

            RoundButton upd = new RoundButton("Update price", WARN);
            RoundButton del = new RoundButton("Delete flight", DANGER);
            upd.setEnabled(false);
            del.setEnabled(false);
            adminTable.getSelectionModel().addListSelectionListener(e -> {
                boolean sel = adminTable.getSelectedRow() >= 0;
                upd.setEnabled(sel);
                del.setEnabled(sel);
            });
            JPanel bottom = box(new FlowLayout(FlowLayout.CENTER, 12, 4));
            bottom.add(upd);
            bottom.add(del);

            add.addActionListener(e -> {
                String fid = id.getText().trim().toUpperCase();
                String fr = from.getText().trim(), tt = to.getText().trim();
                String dp = dep.getText().trim(), ar = arr.getText().trim();
                double pr;
                try {
                    pr = Double.parseDouble(price.getText().trim());
                } catch (NumberFormatException ex) {
                    pr = -1;
                }
                if (fid.isEmpty() || fr.isEmpty() || tt.isEmpty() || fr.equalsIgnoreCase(tt)) {
                    warn("Enter a flight ID and two different cities.");
                    return;
                }
                for (Flight fl : flights) {
                    if (fl.id.equals(fid)) {
                        warn("Flight ID already exists.");
                        return;
                    }
                }
                if (!dp.matches("([01]\\d|2[0-3]):[0-5]\\d") || !ar.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
                    warn("Times must be HH:mm (24-hour).");
                    return;
                }
                if (pr <= 0) {
                    warn("Enter a valid price.");
                    return;
                }
                flights.add(new Flight(fid, fr, tt, dp, ar, pr));
                refreshAdmin();
                populateCombos();
                search();
                for (JTextField t : new JTextField[]{id, from, to, dep, arr, price}) t.setText("");
                setStatus("Flight " + fid + " added.");
            });
            upd.addActionListener(e -> {
                int i = adminTable.getSelectedRow();
                if (i < 0) return;
                Flight fl = flights.get(i);
                String s = JOptionPane.showInputDialog(this, "New base price for " + fl.id + ":", fl.price);
                if (s == null) return;
                try {
                    double np = Double.parseDouble(s.trim());
                    if (np <= 0) throw new NumberFormatException();
                    fl.price = np;
                    refreshAdmin();
                    search();
                    setStatus("Price of " + fl.id + " updated.");
                } catch (NumberFormatException ex) {
                    warn("Enter a valid positive number.");
                }
            });
            del.addActionListener(e -> {
                int i = adminTable.getSelectedRow();
                if (i < 0) return;
                Flight fl = flights.get(i);
                for (Booking b : bookings) {
                    if (b.flight == fl && "CONFIRMED".equals(b.status) && !b.date.isBefore(BOOKING_START)) {
                        warn("Cannot delete: this flight has upcoming confirmed bookings.");
                        return;
                    }
                }
                if (JOptionPane.showConfirmDialog(this, "Delete flight " + fl.id + "?", "Delete",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    flights.remove(fl);
                    refreshAdmin();
                    populateCombos();
                    search();
                    setStatus("Flight " + fl.id + " deleted.");
                }
            });

            Card c = new Card(new BorderLayout(0, 10));
            c.add(scroll(adminTable), BorderLayout.CENTER);
            c.add(bottom, BorderLayout.SOUTH);
            p.add(top, BorderLayout.NORTH);
            p.add(c, BorderLayout.CENTER);
            return p;
        }

        private void refreshAdmin() {
            if (adminModel == null) return;
            adminModel.setRowCount(0);
            for (Flight fl : flights) {
                adminModel.addRow(new Object[]{fl.id, fl.from, fl.to, fl.depart, fl.arrive, fl.durationText(),
                        String.format(Locale.ENGLISH, "%,.2f", fl.price), String.valueOf(fl.seatsLeft(searchDate))});
            }
        }
    }
}
