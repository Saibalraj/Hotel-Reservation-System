import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

// PDFBox imports
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.*;
import org.apache.pdfbox.pdmodel.font.*;

public class HotelReservationSystem {

    // Files
    private static final Path ROOMS_CSV = Paths.get("rooms.csv");
    private static final Path BOOKINGS_CSV = Paths.get("bookings.csv");

    // Date format
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // In-memory data
    static List<Room> rooms = new ArrayList<>();
    static List<Booking> bookings = new ArrayList<>();

    // Current logged-in user
    static User currentUser = null;

    // Default admin credentials
    static final String ADMIN_USER = "admin";
    static final String ADMIN_PASS = "admin123";

    // Swing components (kept at top-level for simplicity)
    private JFrame frame;
    private JTable roomsTable, availTable, bookingsTableAdmin;
    private DefaultTableModel roomsModel, availModel, bookingsAdminModel;
    private JTextField roomNumberField, roomTypeField, roomPriceField;
    private JSpinner bookDateSpinner;
    private JComboBox<String> bookRoomCombo;
    private JTextField customerNameField;
    private JTextField searchRoomField;

    public static void main(String[] args) {
        // load persisted data
        loadRooms();
        loadBookings();

        // create admin default room sample if no rooms
        if (rooms.isEmpty()) {
            rooms.add(new Room(101, "Single", 1200.0));
            rooms.add(new Room(102, "Double", 1800.0));
            rooms.add(new Room(201, "Deluxe", 3000.0));
        }

        SwingUtilities.invokeLater(() -> new HotelAppGui().showLoginDialog());
    }

    // ---------- Models ----------
    static class Room {
        int number;
        String type;
        double price;
        Room(int number, String type, double price) { this.number = number; this.type = type; this.price = price; }
    }

    static class Booking {
        int roomNumber;
        String customer;
        LocalDate date;
        Booking(int roomNumber, String customer, LocalDate date) { this.roomNumber = roomNumber; this.customer = customer; this.date = date; }
    }

    static class User {
        String username;
        boolean isAdmin;
        User(String u, boolean a) { username = u; isAdmin = a; }
    }

    // ---------- Persistence ----------
    private static void loadRooms() {
        rooms.clear();
        try {
            if (!Files.exists(ROOMS_CSV)) return;
            List<String> lines = Files.readAllLines(ROOMS_CSV, StandardCharsets.UTF_8);
            for (String ln : lines) {
                if (ln.trim().isEmpty()) continue;
                String[] p = ln.split(",", -1);
                if (p.length >= 3) {
                    int num = Integer.parseInt(p[0].trim());
                    String type = p[1].trim();
                    double price = Double.parseDouble(p[2].trim());
                    rooms.add(new Room(num, type, price));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void saveRooms() {
        try {
            List<String> lines = rooms.stream()
                    .map(r -> r.number + "," + r.type + "," + r.price)
                    .collect(Collectors.toList());
            Files.write(ROOMS_CSV, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void loadBookings() {
        bookings.clear();
        try {
            if (!Files.exists(BOOKINGS_CSV)) return;
            List<String> lines = Files.readAllLines(BOOKINGS_CSV, StandardCharsets.UTF_8);
            for (String ln : lines) {
                if (ln.trim().isEmpty()) continue;
                String[] p = ln.split(",", -1);
                if (p.length >= 3) {
                    int num = Integer.parseInt(p[0].trim());
                    String cust = p[1].trim();
                    LocalDate dt = LocalDate.parse(p[2].trim(), DATE_FMT);
                    bookings.add(new Booking(num, cust, dt));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void saveBookings() {
        try {
            List<String> lines = bookings.stream()
                    .map(b -> b.roomNumber + "," + b.customer + "," + b.date.format(DATE_FMT))
                    .collect(Collectors.toList());
            Files.write(BOOKINGS_CSV, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ---------- UI: Login ----------
    private void showLoginDialog() {
        JDialog dlg = new JDialog((Frame)null, "Login", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout(10,10));

        JPanel p = new JPanel(new GridLayout(3,2,6,6));
        p.setBorder(BorderFactory.createEmptyBorder(10,10,0,10));
        JTextField userF = new JTextField();
        JPasswordField passF = new JPasswordField();
        p.add(new JLabel("Username:")); p.add(userF);
        p.add(new JLabel("Password:")); p.add(passF);

        dlg.add(p, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton loginBtn = new JButton("Login");
        JButton guestBtn = new JButton("Continue as Guest");
        btns.add(guestBtn); btns.add(loginBtn);
        dlg.add(btns, BorderLayout.SOUTH);

        loginBtn.addActionListener(e -> {
            String u = userF.getText().trim();
            String pss = new String(passF.getPassword());
            if (u.equals(ADMIN_USER) && pss.equals(ADMIN_PASS)) {
                currentUser = new User(u, true);
                dlg.dispose();
                buildAndShowMain();
            } else if (!u.isEmpty()) {
                currentUser = new User(u, false);
                dlg.dispose();
                buildAndShowMain();
            } else {
                JOptionPane.showMessageDialog(dlg, "Invalid credentials or username empty.");
            }
        });

        guestBtn.addActionListener(e -> {
            currentUser = new User("Guest", false);
            dlg.dispose();
            buildAndShowMain();
        });

        dlg.pack();
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
    }

    // ---------- Build main application ----------
    private void buildAndShowMain() {
        frame = new JFrame("Hotel Reservation — Logged in as: " + currentUser.username + (currentUser.isAdmin ? " (Admin)" : ""));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000,700);
        frame.setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();

        // Rooms tab (list rooms)
        tabs.addTab("Rooms", buildRoomsPanel());

        // Book Room tab
        tabs.addTab("Book Room", buildBookingPanel());

        // Availability Calendar tab (date-based)
        tabs.addTab("Availability Calendar", buildAvailabilityPanel());

        // Admin tab (only for admin)
        if (currentUser.isAdmin) {
            tabs.addTab("Admin Panel", buildAdminPanel());
        }

        frame.add(tabs);
        frame.setVisible(true);
    }

    // ---------- Rooms panel ----------
    private JPanel buildRoomsPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        roomsModel = new DefaultTableModel(new Object[]{"Room#", "Type", "Price"},0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        roomsTable = new JTable(roomsModel);
        reloadRoomsModel();

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT,8,8));
        top.add(new JLabel("Room #:"));
        roomNumberField = new JTextField(6);
        top.add(roomNumberField);
        top.add(new JLabel("Type:"));
        roomTypeField = new JTextField(8);
        top.add(roomTypeField);
        top.add(new JLabel("Price:"));
        roomPriceField = new JTextField(6);
        top.add(roomPriceField);

        JButton addRoomBtn = new JButton("Add Room");
        JButton deleteRoomBtn = new JButton("Delete Selected");
        JButton saveRoomsBtn = new JButton("Save Rooms");
        top.add(addRoomBtn); top.add(deleteRoomBtn); top.add(saveRoomsBtn);

        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(roomsTable), BorderLayout.CENTER);

        addRoomBtn.addActionListener(e -> {
            try {
                int num = Integer.parseInt(roomNumberField.getText().trim());
                String type = roomTypeField.getText().trim();
                double price = Double.parseDouble(roomPriceField.getText().trim());
                if (type.isEmpty()) { JOptionPane.showMessageDialog(frame,"Type required"); return; }
                for (Room r: rooms) if (r.number == num) { JOptionPane.showMessageDialog(frame,"Room already exists"); return; }
                rooms.add(new Room(num,type,price));
                saveRooms();
                reloadRoomsModel();
                roomNumberField.setText(""); roomTypeField.setText(""); roomPriceField.setText("");
            } catch (Exception ex) { JOptionPane.showMessageDialog(frame,"Invalid input: " + ex.getMessage()); }
        });

        deleteRoomBtn.addActionListener(e -> {
            int r = roomsTable.getSelectedRow();
            if (r == -1) return;
            int modelRow = roomsTable.convertRowIndexToModel(r);
            int roomNum = (int) roomsModel.getValueAt(modelRow,0);
            // remove any bookings for that room? ask admin
            int yn = JOptionPane.showConfirmDialog(frame,"Also remove any bookings for this room?","Confirm",JOptionPane.YES_NO_CANCEL_OPTION);
            if (yn == JOptionPane.CANCEL_OPTION) return;
            if (yn == JOptionPane.YES_OPTION) {
                bookings.removeIf(b -> b.roomNumber == roomNum);
                saveBookings();
            }
            rooms.removeIf(rr -> rr.number == roomNum);
            saveRooms();
            reloadRoomsModel();
            reloadBookingCombo();
        });

        saveRoomsBtn.addActionListener(e -> { saveRooms(); JOptionPane.showMessageDialog(frame,"Rooms saved."); });

        return p;
    }

    private void reloadRoomsModel() {
        roomsModel.setRowCount(0);
        rooms.sort(Comparator.comparingInt(r->r.number));
        for (Room r : rooms) roomsModel.addRow(new Object[]{r.number, r.type, r.price});
        reloadBookingCombo();
    }

    // ---------- Booking panel ----------
    private JPanel buildBookingPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT,8,8));
        top.add(new JLabel("Customer Name:"));
        customerNameField = new JTextField(16);
        top.add(customerNameField);

        top.add(new JLabel("Date:"));
        bookDateSpinner = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
        JSpinner.DateEditor de = new JSpinner.DateEditor(bookDateSpinner, "yyyy-MM-dd");
        bookDateSpinner.setEditor(de);
        top.add(bookDateSpinner);

        top.add(new JLabel("Room:"));
        bookRoomCombo = new JComboBox<>();
        reloadBookingCombo();
        top.add(bookRoomCombo);

        JButton checkBtn = new JButton("Check Availability");
        JButton bookBtn = new JButton("Book Room");
        top.add(checkBtn); top.add(bookBtn);

        p.add(top, BorderLayout.NORTH);

        // availability table
        availModel = new DefaultTableModel(new Object[]{"Room#", "Type", "Price", "Status"},0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        availTable = new JTable(availModel);
        p.add(new JScrollPane(availTable), BorderLayout.CENTER);

        checkBtn.addActionListener(e -> refreshAvailability());
        bookBtn.addActionListener(e -> doBook());

        return p;
    }

    private void reloadBookingCombo() {
        if (bookRoomCombo == null) return;
        bookRoomCombo.removeAllItems();
        rooms.stream().sorted(Comparator.comparingInt(r->r.number)).forEach(r -> bookRoomCombo.addItem(r.number + " - " + r.type));
    }

    private void refreshAvailability() {
        Date d = (Date) bookDateSpinner.getValue();
        LocalDate ld = Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
        availModel.setRowCount(0);
        for (Room r : rooms) {
            boolean booked = bookings.stream().anyMatch(b -> b.roomNumber == r.number && b.date.equals(ld));
            availModel.addRow(new Object[]{r.number, r.type, r.price, (booked ? "Booked" : "Available")});
        }
    }

    private void doBook() {
        String cust = customerNameField.getText().trim();
        if (cust.isEmpty()) { JOptionPane.showMessageDialog(frame,"Customer name required."); return; }
        if (bookRoomCombo.getItemCount() == 0) { JOptionPane.showMessageDialog(frame,"No rooms."); return; }
        String sel = (String) bookRoomCombo.getSelectedItem();
        int roomNum = Integer.parseInt(sel.split(" - ")[0].trim());
        Date d = (Date) bookDateSpinner.getValue();
        LocalDate ld = Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
        boolean already = bookings.stream().anyMatch(b -> b.roomNumber == roomNum && b.date.equals(ld));
        if (already) { JOptionPane.showMessageDialog(frame,"Room already booked for this date."); return; }
        bookings.add(new Booking(roomNum,cust,ld));
        saveBookings();
        refreshAvailability();
        JOptionPane.showMessageDialog(frame,"Booked room " + roomNum + " for " + cust + " on " + ld.format(DATE_FMT));
        // clear name
        customerNameField.setText("");
    }

    // ---------- Availability panel ----------
    private JPanel buildAvailabilityPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT,8,8));
        top.add(new JLabel("Choose date:"));
        JSpinner dateSpinner = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd"));
        top.add(dateSpinner);
        JButton showBtn = new JButton("Show Bookings");
        top.add(showBtn);

        p.add(top, BorderLayout.NORTH);

        DefaultTableModel calModel = new DefaultTableModel(new Object[]{"Room#", "Type", "Customer"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable calTable = new JTable(calModel);
        p.add(new JScrollPane(calTable), BorderLayout.CENTER);

        showBtn.addActionListener(e -> {
            calModel.setRowCount(0);
            Date d = (Date) dateSpinner.getValue();
            LocalDate ld = Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
            for (Room r : rooms) {
                Optional<Booking> ob = bookings.stream().filter(b -> b.roomNumber==r.number && b.date.equals(ld)).findFirst();
                if (ob.isPresent()) calModel.addRow(new Object[]{r.number, r.type, ob.get().customer});
                else calModel.addRow(new Object[]{r.number, r.type, ""});
            }
        });

        return p;
    }

    // ---------- Admin panel ----------
    private JPanel buildAdminPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT,8,8));
        JButton reloadBtn = new JButton("Reload Data");
        JButton exportCsvBtn = new JButton("Export Bookings CSV");
        JButton exportPdfBtn = new JButton("Export Bookings PDF");
        top.add(reloadBtn); top.add(exportCsvBtn); top.add(exportPdfBtn);
        p.add(top, BorderLayout.NORTH);

        bookingsAdminModel = new DefaultTableModel(new Object[]{"Room#", "Customer", "Date"}, 0) {
            @Override public boolean isCellEditable(int r,int c) { return false; }
        };
        bookingsTableAdmin = new JTable(bookingsAdminModel);
        p.add(new JScrollPane(bookingsTableAdmin), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBookingBtn = new JButton("Cancel Selected Booking");
        bottom.add(cancelBookingBtn);
        p.add(bottom, BorderLayout.SOUTH);

        reloadBtn.addActionListener(e -> reloadBookingsAdmin());
        cancelBookingBtn.addActionListener(e -> cancelSelectedBooking());
        exportCsvBtn.addActionListener(e -> exportBookingsCsvAction());
        exportPdfBtn.addActionListener(e -> exportBookingsPdfAction());

        reloadBookingsAdmin();
        return p;
    }

    private void reloadBookingsAdmin() {
        bookingsAdminModel.setRowCount(0);
        bookings.stream().sorted(Comparator.comparing((Booking b)->b.date).thenComparing(b->b.roomNumber))
                .forEach(b -> bookingsAdminModel.addRow(new Object[]{b.roomNumber, b.customer, b.date.format(DATE_FMT)}));
    }

    private void cancelSelectedBooking() {
        int r = bookingsTableAdmin.getSelectedRow();
        if (r == -1) return;
        int mr = bookingsTableAdmin.convertRowIndexToModel(r);
        int room = (int) bookingsAdminModel.getValueAt(mr, 0);
        String dateS = (String) bookingsAdminModel.getValueAt(mr, 2);
        LocalDate ld = LocalDate.parse(dateS, DATE_FMT);
        bookings.removeIf(b -> b.roomNumber==room && b.date.equals(ld));
        saveBookings();
        reloadBookingsAdmin();
        JOptionPane.showMessageDialog(frame, "Booking canceled.");
    }

    // ---------- Export actions ----------
    private void exportBookingsCsvAction() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("bookings_export.csv"));
        if (fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            Path out = fc.getSelectedFile().toPath();
            try {
                List<String> lines = new ArrayList<>();
                lines.add("room,customer,date");
                for (Booking b : bookings) lines.add(b.roomNumber + "," + b.customer + "," + b.date.format(DATE_FMT));
                Files.write(out, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                JOptionPane.showMessageDialog(frame, "Exported to " + out.toString());
            } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "Export failed: " + ex.getMessage()); }
        }
    }

    private void exportBookingsPdfAction() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("bookings_report.pdf"));
        if (fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            Path out = fc.getSelectedFile().toPath();
            try {
                createPdfReport(out.toFile());
                JOptionPane.showMessageDialog(frame, "PDF exported to " + out.toString());
            } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "PDF export failed: " + ex.getMessage()); ex.printStackTrace(); }
        }
    }

    // Using Apache PDFBox to generate a simple table-like PDF
    private void createPdfReport(File outFile) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            PDPageContentStream cs = new PDPageContentStream(doc, page);
            PDFont font = PDType1Font.HELVETICA;

            float y = page.getMediaBox().getHeight() - 50;
            float margin = 50;
            float startX = margin;
            cs.beginText();
            cs.setFont(font, 14);
            cs.newLineAtOffset(startX, y);
            cs.showText("Bookings Report");
            cs.endText();

            y -= 25;
            cs.beginText();
            cs.setFont(font, 10);
            cs.newLineAtOffset(startX, y);
            cs.showText(String.format("%-10s %-25s %-12s", "Room","Customer","Date"));
            cs.endText();
            y -= 15;

            for (Booking b : bookings) {
                if (y < 60) { cs.close(); page = new PDPage(PDRectangle.LETTER); doc.addPage(page); cs = new PDPageContentStream(doc, page); y = page.getMediaBox().getHeight() - 50; }
                cs.beginText();
                cs.setFont(font, 10);
                cs.newLineAtOffset(startX, y);
                String line = String.format("%-10s %-25s %-12s", b.roomNumber, truncate(b.customer,25), b.date.format(DATE_FMT));
                cs.showText(line);
                cs.endText();
                y -= 14;
            }

            cs.close();
            doc.save(outFile);
        }
    }

    private static String truncate(String s, int n) { return s.length()<=n ? s : s.substring(0,n-3)+"..."; }

    // ---------- Helper utilities ----------
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static void reloadAvailabilityModel() {
        // not used globally, availability refreshed on demand
    }

    // ---------- simple helpers to save data ----------
    private static void persistAll() {
        saveRooms();
        saveBookings();
    }

    // ---------- END ----------
}

