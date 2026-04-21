package com.pusula.desktop.util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Türkiye Para Birimi formatında giriş yapılabilen özel TextField bileşeni.
 *
 * <h3>Kullanıcı Deneyimi:</h3>
 * <ul>
 *   <li>Sadece rakam ve virgül girişine izin verir (harfler engellenir)</li>
 *   <li>Yazarken otomatik binlik ayraç (nokta) ekler: 1.234.567</li>
 *   <li>Ondalık kısım için virgül kullanılır: 1.234,50</li>
 *   <li>Maksimum 2 ondalık basamak</li>
 * </ul>
 *
 * <h3>Backend Entegrasyonu:</h3>
 * <ul>
 *   <li>{@link #getRawValue()} — Noktasız/virgülsüz saf BigDecimal döndürür</li>
 *   <li>{@link #getDoubleValue()} — double olarak döndürür</li>
 *   <li>{@link #setRawValue(BigDecimal)} — BigDecimal'i formatla set eder</li>
 * </ul>
 *
 * <h3>Örnek Kullanım:</h3>
 * <pre>
 * CurrencyTextField field = new CurrencyTextField();
 * field.setPromptText("Tutar (₺)");
 *
 * // Kullanıcı "12500,75" yazarken ekranda "12.500,75" görür
 *
 * // Backend'e gönderirken:
 * BigDecimal rawValue = field.getRawValue(); // 12500.75
 * </pre>
 *
 * @author Pusula Desktop Team
 * @since 2.0
 */
public class CurrencyTextField extends TextField {

    private static final Locale TR_LOCALE = new Locale("tr", "TR");
    private static final DecimalFormatSymbols TR_SYMBOLS = new DecimalFormatSymbols(TR_LOCALE);

    // Formatting state
    private boolean internalUpdate = false;

    public CurrencyTextField() {
        this("");
    }

    public CurrencyTextField(String initialValue) {
        super();
        setPromptText("0,00");
        setupFormatter();
        if (initialValue != null && !initialValue.isEmpty()) {
            setText(initialValue);
        }
    }

    /**
     * TextFormatter ile klavye girişini kontrol eder.
     * - Sadece rakam ve virgül girişine izin verir
     * - Birden fazla virgül engellenir
     * - Virgülden sonra max 2 basamak
     * - Anlık olarak binlik nokta formatlaması yapar
     */
    private void setupFormatter() {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            if (internalUpdate) {
                return change;
            }

            String newText = change.getControlNewText();

            // Boş metin her zaman kabul
            if (newText.isEmpty()) {
                return change;
            }

            // Kullanıcı nokta yazarsa, virgüle çevir (Türk klavye uyumluluğu)
            // Ama dikkat: formatlama sırasında binlik nokta ekleniyor
            String inputChar = change.getText();
            if (".".equals(inputChar)) {
                // Nokta girişini virgüle çevir
                if (newText.contains(",")) {
                    // Zaten virgül var, ikinciye izin verme
                    return null;
                }
                change.setText(",");
                newText = change.getControlNewText();
            }

            // Formatlanmış metinden raw sayıyı çıkar (noktaları kaldır)
            String rawInput = newText.replace(".", "");

            // Sadece rakam ve virgül kontrolü
            if (!rawInput.matches("^\\d*,?\\d{0,2}$")) {
                return null;
            }

            // Virgülden sonra max 2 basamak
            if (rawInput.contains(",")) {
                String[] parts = rawInput.split(",", -1);
                if (parts.length > 2) {
                    return null;
                }
                if (parts.length == 2 && parts[1].length() > 2) {
                    return null;
                }
            }

            // Formatlama uygula
            String formatted = formatWithThousandSeparator(rawInput);

            // Değişikliği yeniden yapılandır
            internalUpdate = true;
            int oldCaretPos = change.getCaretPosition();
            int oldTextLength = change.getControlText().length();

            change.setRange(0, change.getControlText().length());
            change.setText(formatted);

            // Caret pozisyonunu akıllıca hesapla
            int addedDots = formatted.length() - rawInput.length();
            int newCaretPos;

            if (change.isAdded()) {
                // Karakter eklendi
                int rawCaretPos = calculateRawCaretPosition(change.getControlText(), oldCaretPos);
                rawCaretPos += change.getText().length() - (oldTextLength);
                rawCaretPos = Math.max(0, Math.min(rawCaretPos, rawInput.length()));
                newCaretPos = calculateFormattedCaretPosition(formatted, rawCaretPos);
            } else if (change.isDeleted()) {
                // Karakter silindi
                int rawCaretPos = calculateRawCaretPosition(change.getControlText(), oldCaretPos);
                rawCaretPos = Math.max(0, Math.min(rawCaretPos, rawInput.length()));
                newCaretPos = calculateFormattedCaretPosition(formatted, rawCaretPos);
            } else {
                newCaretPos = formatted.length();
            }

            newCaretPos = Math.max(0, Math.min(newCaretPos, formatted.length()));
            change.setCaretPosition(newCaretPos);
            change.setAnchor(newCaretPos);
            internalUpdate = false;

            return change;
        };

        setTextFormatter(new TextFormatter<>(filter));
    }

    /**
     * Raw sayı stringini Türk formatında binlik ayraçlı hale getirir.
     * Örn: "1234567" → "1.234.567"
     * Örn: "1234567,50" → "1.234.567,50"
     */
    private String formatWithThousandSeparator(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String intPart;
        String decPart = null;

        if (raw.contains(",")) {
            String[] parts = raw.split(",", -1);
            intPart = parts[0];
            decPart = parts.length > 1 ? parts[1] : "";
        } else {
            intPart = raw;
        }

        // Başındaki gereksiz sıfırları temizle (tek '0' kalabilir)
        if (intPart.length() > 1 && intPart.startsWith("0")) {
            intPart = intPart.replaceFirst("^0+", "");
            if (intPart.isEmpty()) {
                intPart = "0";
            }
        }

        // Binlik nokta ekle
        StringBuilder formatted = new StringBuilder();
        int count = 0;
        for (int i = intPart.length() - 1; i >= 0; i--) {
            if (count > 0 && count % 3 == 0) {
                formatted.insert(0, '.');
            }
            formatted.insert(0, intPart.charAt(i));
            count++;
        }

        if (decPart != null) {
            formatted.append(',').append(decPart);
        }

        return formatted.toString();
    }

    /**
     * Formatlanmış metindeki caret pozisyonunu raw (nokta olmadan) pozisyona çevirir.
     */
    private int calculateRawCaretPosition(String formattedText, int caretPos) {
        int rawPos = 0;
        for (int i = 0; i < Math.min(caretPos, formattedText.length()); i++) {
            if (formattedText.charAt(i) != '.') {
                rawPos++;
            }
        }
        return rawPos;
    }

    /**
     * Raw pozisyonu formatlanmış metindeki gerçek pozisyona çevirir.
     */
    private int calculateFormattedCaretPosition(String formattedText, int rawPos) {
        int currentRawPos = 0;
        for (int i = 0; i < formattedText.length(); i++) {
            if (formattedText.charAt(i) != '.') {
                if (currentRawPos == rawPos) {
                    return i;
                }
                currentRawPos++;
            }
        }
        return formattedText.length();
    }

    // ==================== PUBLIC API ====================

    /**
     * Ekrandaki formatlanmış değeri BigDecimal olarak döndürür.
     * Backend'e gönderilecek saf sayısal değer.
     *
     * @return BigDecimal değer (null-safe, boşsa BigDecimal.ZERO)
     * @throws NumberFormatException geçersiz format durumunda
     *
     * Kullanım: {@code BigDecimal amount = currencyField.getRawValue();}
     */
    public BigDecimal getRawValue() {
        String text = getText();
        if (text == null || text.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Binlik noktaları kaldır, virgülü noktaya çevir
        String normalized = text
                .replace(".", "")   // Binlik ayraçları kaldır
                .replace(",", "."); // Ondalık virgülü noktaya çevir

        return new BigDecimal(normalized);
    }

    /**
     * Ekrandaki değeri double olarak döndürür.
     * Küçük tutarlarda kullanılabilir ancak hassas hesaplarda
     * {@link #getRawValue()} tercih edilmelidir.
     *
     * @return double değer (boşsa 0.0)
     */
    public double getDoubleValue() {
        return getRawValue().doubleValue();
    }

    /**
     * BigDecimal değeri formatlanmış şekilde metin kutusuna set eder.
     * Genellikle düzenleme modunda var olan veriyi göstermek için kullanılır.
     *
     * @param value set edilecek değer (null ise boş bırakılır)
     *
     * Kullanım: {@code currencyField.setRawValue(expense.getAmount());}
     */
    public void setRawValue(BigDecimal value) {
        if (value == null) {
            setText("");
            return;
        }

        // BigDecimal'i Türk formatına çevir
        DecimalFormat df = new DecimalFormat("#,##0.##", TR_SYMBOLS);
        String formatted = df.format(value);
        setText(formatted);
    }

    /**
     * Mevcut değerin geçerli olup olmadığını kontrol eder.
     *
     * @return true - değer geçerli ve sıfırdan büyük
     */
    public boolean isValidAmount() {
        try {
            BigDecimal value = getRawValue();
            return value.compareTo(BigDecimal.ZERO) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Mevcut değerin boş olup olmadığını kontrol eder.
     *
     * @return true - metin kutusu boş veya null
     */
    public boolean isEmpty() {
        String text = getText();
        return text == null || text.trim().isEmpty();
    }

    /**
     * Statik yardımcı metot: Herhangi bir String'i Türk para formatına çevirir.
     * Kullanıcının "1234.56" veya "1234,56" olarak girdiği her iki formatı da normalize eder.
     *
     * @param input kullanıcı girişi (herhangi bir formatta)
     * @return BigDecimal (parse edilmiş saf sayı)
     * @throws NumberFormatException geçersiz format
     */
    public static BigDecimal parseTurkishCurrency(String input) {
        if (input == null || input.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        String normalized = input.trim();

        // Eğer hem nokta hem virgül varsa, son ayracı ondalık kabul et
        boolean hasDot = normalized.contains(".");
        boolean hasComma = normalized.contains(",");

        if (hasDot && hasComma) {
            // Türk formatı: 1.234,56 → noktalar binlik, virgül ondalık
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (hasComma) {
            // Sadece virgül var: 1234,56 → virgül ondalık
            normalized = normalized.replace(",", ".");
        }
        // Sadece nokta varsa: 1234.56 → zaten doğru format

        return new BigDecimal(normalized);
    }
}
