package com.pusula.desktop.controller;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UnifiedExpenseDialogLayoutTest {

    @Test
    void dailySaveActionStaysInFixedFooterOutsideScrollableForm() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/view/unified_expense_dialog.fxml")) {
            assertNotNull(input);
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);

            NodeList actionBars = document.getElementsByTagName("HBox");
            Element footer = null;
            for (int i = 0; i < actionBars.getLength(); i++) {
                Element candidate = (Element) actionBars.item(i);
                if (candidate.getAttribute("styleClass").contains("dialog-action-bar")) {
                    footer = candidate;
                    break;
                }
            }

            assertNotNull(footer);
            NodeList buttons = footer.getElementsByTagName("Button");
            assertEquals(2, buttons.getLength());
            assertEquals("#handleDailySave", ((Element) buttons.item(1)).getAttribute("onAction"));
            assertEquals("true", ((Element) buttons.item(1)).getAttribute("defaultButton"));
        }
    }
}
