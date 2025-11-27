# WHERE TO ADD THE CODE - Visual Guide

Open: `backend/src/main/java/com/pusula/backend/service/ReportService.java`

## STEP 1: Add Imports (at the top, after line 12)

**Current location:**
```java
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
                                          <-- ADD HERE
@Service
public class ReportService {
```

**Add these 5 lines:**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
```

---

## STEP 2: Add Repository Fields (around line 19-21)

**Current location:**
```java
    private final ServiceTicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
                                          <-- ADD HERE
    private static final DateTimeFormatter DATE_FORMAT = ...
```

**Add these 2 lines:**
```java
    private final DailyClosingRepository dailyClosingRepository;
    private final ExpenseRepository expenseRepository;
```

---

## STEP 3: Replace Font Constants (around line 24-27)

**FIND and DELETE these lines:**
```java
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
    private static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
```

**REPLACE with these 22 lines:**
```java
    private static BaseFont turkishBaseFont;
    private static Font HEADER_FONT;
    private static Font SECTION_FONT;
    private static Font NORMAL_FONT;
    private static Font SMALL_FONT;
    private static Font SMALL_BOLD_FONT;
    
    static {
        try {
            turkishBaseFont = BaseFont.createFont("/fonts/FreeSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            HEADER_FONT = new Font(turkishBaseFont, 18, Font.BOLD, Color.BLACK);
            SECTION_FONT = new Font(turkishBaseFont, 12, Font.BOLD, Color.BLACK);
            NORMAL_FONT = new Font(turkishBaseFont, 10, Font.NORMAL, Color.BLACK);
            SMALL_FONT = new Font(turkishBaseFont, 8, Font.NORMAL, Color.GRAY);
            SMALL_BOLD_FONT = new Font(turkishBaseFont, 8, Font.BOLD, Color.BLACK);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Turkish font", e);
        }
    }
```

---

## STEP 4: Update Constructor (around line 29-35)

**FIND this:**
```java
    public ReportService(ServiceTicketRepository ticketRepository,
            CustomerRepository customerRepository,
            CompanyRepository companyRepository) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
    }
```

**REPLACE with:**
```java
    public ReportService(ServiceTicketRepository ticketRepository,
            CustomerRepository customerRepository,
            CompanyRepository companyRepository,
            DailyClosingRepository dailyClosingRepository,
            ExpenseRepository expenseRepository) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
        this.dailyClosingRepository = dailyClosingRepository;
        this.expenseRepository = expenseRepository;
    }
```

---

## STEP 5: Add Methods at End (before the final closing brace)

**Scroll to the very bottom of ReportService.java**

**FIND:**
```java
    private void addFooter(Document document) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));
        
        Paragraph signatureLabel = new Paragraph("Müşteri İmzası: _____________________", NORMAL_FONT);
        document.add(signatureLabel);
        
        document.add(new Paragraph(" "));
        
        Paragraph terms = new Paragraph(
                "Koşullar: Yapılan işler için 30 gün garanti verilmektedir. " +
                        "Garanti süresi içerisinde aynı arıza için ücretsiz servis hizmeti sunulmaktadır.",
                SMALL_FONT);
        terms.setAlignment(Element.ALIGN_JUSTIFIED);
        document.add(terms);
    }
}  <-- FINAL CLOSING BRACE
```

**BEFORE the final `}`, add ALL the methods from the file:**
- `getMonthlyArchives()`
- `generateMonthlyPDF()`
- `addFinancialRow()`
- `getCategoryNameTurkish()`

**Result should look like:**
```java
    private void addFooter(Document document) throws DocumentException {
        ... existing code ...
    }

    // ========== MONTHLY FINANCIAL REPORTS ==========
    
    public List<com.pusula.backend.dto.MonthlySummaryDTO> getMonthlyArchives(Long companyId) {
        ... (copy entire method from MONTHLY_PDF_CODE_TO_ADD.java) ...
    }
    
    public byte[] generateMonthlyPDF(java.time.YearMonth period, Long companyId) throws DocumentException {
        ... (copy entire method from MONTHLY_PDF_CODE_TO_ADD.java) ...
    }
    
    private void addFinancialRow(PdfPTable table, String label, String value) {
        ... (copy entire method from MONTHLY_PDF_CODE_TO_ADD.java) ...
    }
    
    private String getCategoryNameTurkish(String category) {
        ... (copy entire method from MONTHLY_PDF_CODE_TO_ADD.java) ...
    }
}  <-- KEEP THIS FINAL CLOSING BRACE
```

---

## Quick Checklist

- [ ] Added 5 imports at top
- [ ] Added 2 repository fields
- [ ] Replaced 4 font constants with static block (22 lines)
- [ ] Updated constructor (added 2 parameters, 2 assignments)
- [ ] Added 4 methods at end (before final `}`)
- [ ] File still compiles (no red errors)

**Then restart backend to test!**
