package com.vaadin.demo.nordicsupply.examples;

import static com.vaadin.demo.nordicsupply.examples.DocumentPainter.A4_WIDTH;
import static com.vaadin.demo.nordicsupply.examples.DocumentPainter.GREY;
import static com.vaadin.demo.nordicsupply.examples.DocumentPainter.INK;
import static com.vaadin.demo.nordicsupply.examples.DocumentPainter.PEN;
import static com.vaadin.demo.nordicsupply.examples.DocumentPainter.RULE;
import static com.vaadin.demo.nordicsupply.examples.DocumentPainter.STAMP;
import static com.vaadin.demo.nordicsupply.examples.DocumentPainter.lineHeight;
import static com.vaadin.demo.nordicsupply.examples.DocumentPainter.mono;
import static com.vaadin.demo.nordicsupply.examples.DocumentPainter.sans;
import static com.vaadin.demo.nordicsupply.examples.DocumentPainter.sansBold;
import static com.vaadin.demo.nordicsupply.examples.DocumentPainter.serif;
import static com.vaadin.demo.nordicsupply.examples.DocumentPainter.serifBold;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.List;

import com.vaadin.flow.component.ai.common.AIAttachment;
import org.springframework.stereotype.Component;

import com.vaadin.demo.nordicsupply.util.Images;
import com.vaadin.demo.nordicsupply.util.Pdfs;

/**
 * The example documents of the Claims view: six complaints from customers of the dataset, one per country, each a
 * different kind of document and a different kind of claim. Every name, number, product and price in them is in the
 * data, so the assistant can find the customer, the order and the shipment it reads about. They are painted at
 * start-up by {@link DocumentPainter}, PDFs and images alike, so the repository holds no binary files and what each
 * document says is written here. The PDFs carry their text as an OCR'd scan does, under the page image, so the view
 * outlines a value's source on the words themselves; the images carry none, as a screenshot or a photo does not.
 * <ul>
 * <li>{@code Damaged pallet.pdf}: a Finnish store's exported email with the scanned delivery note the driver wrote
 * the damage on, two pages.</li>
 * <li>{@code Missing cartons.png}: a screenshot of a Swedish web shop's message in the customer portal.</li>
 * <li>{@code Late delivery.pdf}: a German store's letter asking for compensation.</li>
 * <li>{@code Quality defect.jpg}: a photo of a quality report form a Norwegian store filled in by hand.</li>
 * <li>{@code Pricing dispute.pdf}: a Danish department store's scan of an invoice with the dispute written on
 * it.</li>
 * <li>{@code Wrong colour.png}: a screenshot of an Estonian web shop's chat with its account manager.</li>
 * </ul>
 */
@Component
public class ExampleDocuments {

    private static final String PNG = "image/png";
    private static final String JPEG = "image/jpeg";

    /** The margin of an A4 page, and the width of its text. */
    private static final int MARGIN = 110;

    private static final int TEXT_WIDTH = A4_WIDTH - 2 * MARGIN;

    private static final float PHOTO_QUALITY = 0.82f;

    /** The margin under the exported email's signature, where its page ends. */
    private static final int EMAIL_BOTTOM_MARGIN = 80;

    /** The chat app's colours: its bars, the other side's bubbles and one's own, its text and its faint text. */
    private static final Color CHAT_BAR = new Color(0x12, 0x20, 0x29);

    private static final Color CHAT_THEIRS = new Color(0x1f, 0x2c, 0x34);
    private static final Color CHAT_MINE = new Color(0x00, 0x5c, 0x4b);
    private static final Color CHAT_TEXT = new Color(0xe9, 0xed, 0xef);
    private static final Color CHAT_FAINT = new Color(0x8e, 0x9a, 0xa1);

    private final List<ExampleDocument> all;

    public ExampleDocuments() {
        all = List.of(
                damagedPallet(), missingCartons(), lateDelivery(), qualityDefect(), pricingDispute(), wrongColour());
    }

    /** Every example, in the order they are shown. */
    public List<ExampleDocument> all() {
        return all;
    }

    /** The examples whose customers are in the given countries: those a region's account can register. */
    public List<ExampleDocument> forCountries(Collection<String> countries) {
        return all.stream()
                .filter(example -> countries.contains(example.country()))
                .toList();
    }

    private static ExampleDocument pdf(String name, String kind, String country, List<Pdfs.Page> pages) {
        return new ExampleDocument(
                new AIAttachment(name, Pdfs.MIME_TYPE, Pdfs.fromPages(pages, DocumentPainter.DPI)), kind, country);
    }

    private static ExampleDocument png(String name, String kind, String country, BufferedImage picture) {
        return new ExampleDocument(new AIAttachment(name, PNG, Images.png(picture)), kind, country);
    }

    private static ExampleDocument jpeg(String name, String kind, String country, BufferedImage picture) {
        return new ExampleDocument(new AIAttachment(name, JPEG, Images.jpeg(picture, PHOTO_QUALITY)), kind, country);
    }

    /**
     * Ridge Sport & Fritid Oy, Rovaniemi: a crushed pallet, with the delivery note as the second page. The email is
     * exported to a page of its own height, as mail clients print to PDF, so the text fills it; on a page that is
     * mostly blank the model places its source rectangles above the text.
     */
    private static ExampleDocument damagedPallet() {
        var email = DocumentPainter.a4(1);
        int y = emailHeader(
                email,
                "Pekka Koskinen <pekka.koskinen@ridge.sport.fritid.example>",
                "claims@nordicsupply.example",
                "Tuesday, 1 September 2026 at 09:14",
                "Damaged pallet, order SO-2026-006830 / SH-2026-007291",
                "delivery-note-SH-2026-007291.jpg (page 2)");
        var body = sans(28);
        y = email.paragraphs(
                MARGIN,
                y,
                TEXT_WIDTH,
                body,
                INK,
                20,
                "Hello,",
                "Your shipment SH-2026-007291 for our order SO-2026-006830 (our reference PO-24154) was delivered by"
                        + " Kalottfrakt on Friday 28 August. The pallet arrived with its top layer crushed and the"
                        + " stretch wrap torn open. The driver noted the damage on the delivery note, which I have"
                        + " attached as a scan.",
                "Damaged: 6 pcs Nordvind Hav 2.0 Hardshell Jacket, size L, Burnt Orange (NVD-SHL-0026). The two top"
                        + " cartons were crushed and the jackets inside are soaked and stained. The other 90 jackets"
                        + " and the remaining lines are undamaged.",
                "We ask for a replacement of the 6 jackets, value 6 x 197.90 = 1 187.40 EUR net. Our autumn season"
                        + " opens on 3 October, so we need the replacements by 25 September 2026 at the latest. The"
                        + " damaged jackets are kept here for your inspection.",
                "Best regards,");
        y = email.paragraph(
                "Pekka Koskinen\nStore Manager, Ridge Sport & Fritid Oy\nKauppakatu 5, 96100 Rovaniemi, Finland"
                        + "\n+358 40 154 2114",
                MARGIN,
                y,
                TEXT_WIDTH,
                body,
                INK);

        var note = DocumentPainter.a4(2);
        note.text("NORDIC SUPPLY", MARGIN, 190, sansBold(46), INK)
                .text("Delivery note", MARGIN, 240, sans(30), GREY)
                .textRight("SH-2026-007291", A4_WIDTH - MARGIN, 190, sansBold(36), INK)
                .textRight("Shipped 27 August 2026", A4_WIDTH - MARGIN, 240, sans(26), GREY)
                .rule(MARGIN, 280, A4_WIDTH - MARGIN, 280, INK, 2f);
        var detail = sans(26);
        note.paragraph(
                "Order SO-2026-006830\nYour reference PO-24154\nFrom Vantaa Central Warehouse, Vantaa"
                        + "\nCarrier Kalottfrakt, tracking KA9456355659\n1 pallet, 6 packages, 208.3 kg",
                MARGIN,
                340,
                560,
                detail,
                INK);
        note.text("Deliver to", 720, 340, sansBold(26), GREY)
                .paragraph(
                        "Ridge Sport & Fritid Oy\nKauppakatu 5\n96100 Rovaniemi\nFinland", 720, 380, 420, detail, INK);
        int table = 600;
        note.rule(MARGIN, table, A4_WIDTH - MARGIN, table, INK, 1.5f)
                .text("Qty", MARGIN, table + 40, sansBold(24), GREY)
                .text("Product", MARGIN + 100, table + 40, sansBold(24), GREY)
                .text("SKU", 900, table + 40, sansBold(24), GREY)
                .rule(MARGIN, table + 58, A4_WIDTH - MARGIN, table + 58, RULE, 1f);
        String[][] lines = {
            {"24", "Kajakk Skog 2.0 Spray Skirt XL Lichen", "KAJ-WTR-0013"},
            {"12", "Øresund Elv Plus Pannier Pair 28L Black", "ORE-CYC-0019"},
            {"12", "Taiga Selja 2.0 Merino Base Layer Top XL Petrol", "TAI-BAS-0017"},
            {"96", "Nordvind Hav 2.0 Hardshell Jacket L Burnt Orange", "NVD-SHL-0026"},
        };
        int row = table + 104;
        for (var line : lines) {
            note.text(line[0], MARGIN, row, detail, INK)
                    .text(line[1], MARGIN + 100, row, detail, INK)
                    .text(line[2], 900, row, mono(24), INK)
                    .rule(MARGIN, row + 18, A4_WIDTH - MARGIN, row + 18, RULE, 1f);
            row += 56;
        }
        int remarks = row + 60;
        note.text("Receiver's remarks", MARGIN, remarks, sansBold(26), GREY)
                .box(MARGIN, remarks + 20, TEXT_WIDTH, 330, null, INK, 8)
                .handwriting("Top layer crushed, stretch wrap torn.", MARGIN + 30, remarks + 90, 40, PEN)
                .handwriting("2 cartons wet - 6 jackets damaged", MARGIN + 30, remarks + 160, 40, PEN)
                .handwriting("28.8.2026", MARGIN + 30, remarks + 290, 36, PEN)
                .signature(MARGIN + 560, remarks + 290, 300, PEN)
                .handwriting("P. Koskinen", MARGIN + 580, remarks + 330, 30, PEN)
                .stamp("RECEIVED WITH REMARKS", 560, remarks + 400, STAMP, -6);
        note.text("Signed for the receiver", MARGIN, remarks + 440, sans(22), GREY);
        return pdf(
                "Damaged pallet.pdf",
                "Exported email with the delivery note, 2 pages",
                "FI",
                List.of(email.page(y + EMAIL_BOTTOM_MARGIN), note.scanned()));
    }

    /** Forestshop AB, Uppsala: a package short, written in the customer portal. */
    private static ExampleDocument missingCartons() {
        var shot = new DocumentPainter(1400, 1000, new Color(0xf3, 0xf4, 0xf6), 3);
        var chrome = new Color(0xde, 0xe1, 0xe6);
        shot.box(0, 0, 1400, 72, chrome, null, 0)
                .box(160, 16, 1080, 40, Color.WHITE, null, 20)
                .text("portal.nordicsupply.example/messages/4821", 190, 44, sans(22), GREY);
        shot.box(0, 72, 1400, 64, new Color(0x16, 0x16, 0x16), null, 0)
                .text("NORDIC SUPPLY", 40, 114, sansBold(24), new Color(0xff, 0xd1, 0x3b))
                .text("Customer Portal", 300, 114, sans(24), Color.WHITE)
                .textRight("Forestshop AB  |  C-11610", 1360, 114, sans(22), new Color(0xc8, 0xc8, 0xc8));
        String[] menu = {"Orders", "Shipments", "Invoices", "Messages", "Returns"};
        int item = 190;
        for (var entry : menu) {
            boolean current = entry.equals("Messages");
            if (current) {
                shot.box(24, item - 32, 190, 46, Color.WHITE, null, 10);
            }
            shot.text(entry, 44, item, current ? sansBold(24) : sans(24), current ? INK : GREY);
            item += 60;
        }
        int card = 240;
        shot.box(card, 160, 1120, 800, Color.WHITE, new Color(0xd9, 0xdc, 0xe1), 16)
                .text("Messages  >  Short delivery, order SO-2026-006314", card + 40, 215, sansBold(28), INK)
                .text(
                        "Astrid Larsson, Store Manager  |  Forestshop AB  |  3 September 2026, 14:02",
                        card + 40,
                        258,
                        sans(21),
                        GREY)
                .rule(card + 40, 285, card + 1080, 285, RULE, 1f);
        shot.paragraphs(
                card + 40,
                340,
                1040,
                sans(24),
                INK,
                14,
                "Hi,",
                "Shipment SH-2026-006754 (Kalott Freight, tracking KA1220942516) was delivered yesterday, 2 September."
                        + " The packing list shows 5 packages, but only 4 arrived, and the driver had only 4 on his"
                        + " scanner.",
                "The missing package should contain 6 pcs Jäkälä Nordic Plus Softshell Jacket S Burnt Orange"
                        + " (SIS-SHL-0045). We received 18 of the 24 ordered. The backpack on the same order, Skarvind"
                        + " Revontuli 2.0 Backpack 55L Forest, arrived fine.",
                "Please send the 6 missing jackets as soon as possible; we would rather have the goods than a credit."
                        + " Value of the missing jackets: 6 x 152.90 = 917.40 EUR before our 5 % discount.",
                "Regards,\nAstrid");
        shot.box(card + 40, 860, 900, 60, new Color(0xf7, 0xf7, 0xf8), new Color(0xd9, 0xdc, 0xe1), 12)
                .text("Write a reply...", card + 62, 898, sans(22), new Color(0x9a, 0x9a, 0x9a))
                .box(card + 960, 860, 120, 60, new Color(0x16, 0x16, 0x16), null, 12)
                .text("Send", card + 992, 898, sansBold(22), Color.WHITE);
        return png(
                "Missing cartons.png",
                "Screenshot of a portal message",
                "SE",
                shot.page().image());
    }

    /** Berlin Fritid GmbH: a letter asking compensation for a delivery six days late. */
    private static ExampleDocument lateDelivery() {
        var letter = DocumentPainter.a4(4);
        letter.text("BERLIN FRITID GmbH", MARGIN, 170, serifBold(44), INK)
                .text("Outdoor  -  Reise  -  Beratung", MARGIN, 212, serif(24), GREY)
                .textRight("Bergweg 95  -  10115 Berlin", A4_WIDTH - MARGIN, 170, serif(22), GREY)
                .textRight("+49 40 0186186  -  USt-IdNr. DE095602441", A4_WIDTH - MARGIN, 204, serif(22), GREY)
                .rule(MARGIN, 250, A4_WIDTH - MARGIN, 250, INK, 2f);
        var body = serif(28);
        letter.paragraph(
                "Nordic Supply Oy\nClaims Department\nclaims@nordicsupply.example", MARGIN, 360, 600, body, INK);
        letter.textRight("Berlin, 4 September 2026", A4_WIDTH - MARGIN, 360, body, INK);
        int y = 560;
        y = letter.paragraph(
                        "Late delivery of order SO-2026-006433 - request for compensation",
                        MARGIN,
                        y,
                        TEXT_WIDTH,
                        serifBold(30),
                        INK)
                + 30;
        // the signature goes in the room under the closing
        y = letter.paragraphs(
                        MARGIN,
                        y,
                        TEXT_WIDTH,
                        body,
                        INK,
                        20,
                        "Dear Sir or Madam,",
                        "Our order SO-2026-006433, placed on 10 August 2026 through your portal, was confirmed for"
                                + " delivery on 18 August 2026. The first part of it, shipment SH-2026-006877 (Kalott"
                                + " Freight, tracking KA1151121507), reached us only on 24 August, six days after the"
                                + " confirmed date.",
                        "The shipment carried 12 pairs Peak Trail WP Hiking Boot EU 38 Ochre and 8 pairs Gråsten"
                                + " Kallio Eco Winter Boot EU 41 Graphite that we had bought for our back-to-school"
                                + " promotion, which ran from 17 to 23 August. The goods arrived after the promotion"
                                + " had ended, and part of them had to be sold at a reduced price.",
                        "Under section 7 of our framework agreement we request a credit of 10 % of the order value,"
                                + " 359.27 EUR, as compensation for the late delivery. We are not returning any goods."
                                + " Please confirm the credit by 18 September 2026.",
                        "Yours faithfully,")
                + 50;
        letter.signature(MARGIN, y, 320, PEN);
        letter.paragraph(
                "Katharina Wagner\nOwner, Berlin Fritid GmbH\nkatharina.wagner@berlin.fritid.example"
                        + "  -  +49 40 5367848",
                MARGIN,
                y + 50,
                TEXT_WIDTH,
                body,
                INK);
        return pdf("Late delivery.pdf", "Letter, 1 page", "DE", List.of(letter.page()));
    }

    /** Polar Adventure AS, Oslo: a quality report form filled in by hand and photographed. */
    private static ExampleDocument qualityDefect() {
        var form = new DocumentPainter(1200, 1800, DocumentPainter.PAPER, 5);
        int left = 90;
        int right = 1110;
        form.text("NORDIC SUPPLY", left, 130, sansBold(40), INK)
                .text("Quality report / return request", left, 180, sans(30), GREY)
                .textRight("Form QR-1", right, 130, sansBold(26), GREY)
                .textRight("Send a photo of this form to claims@nordicsupply.example", right, 178, sans(18), GREY)
                .rule(left, 210, right, 210, INK, 2f);
        int y = 300;
        y = formField(form, "Customer", left, y, right - left, "Polar Adventure AS  (C-11446)");
        formField(form, "Contact", left, y, 560, "Eirik Bakke, Store Manager");
        y = formField(form, "Phone", left + 620, y, right - left - 620, "+47 936 11 425");
        formField(form, "Order no.", left, y, 480, "SO-2026-006423");
        y = formField(form, "Shipment no.", left + 540, y, right - left - 540, "SH-2026-006866");
        y = formField(form, "Date", left, y, 300, "2.9.2026");
        formField(form, "Product", left, y, 700, "Älv Ridge Cookset Graphite");
        y = formField(form, "SKU", left + 760, y, right - left - 760, "ALV-KIT-0020");
        y = formField(form, "Quantity affected", left, y, 420, "4 of 30");
        form.text("Description of the defect", left, y, sansBold(22), GREY);
        String[] description = {
            "Pot handles come loose - the rivets are not fixed.",
            "2 sets returned by end customers, 2 more found in our stock.",
            "Safety issue, hot water! We have taken the rest off the",
            "shelf until you confirm.",
        };
        int line = y + 60;
        for (var sentence : description) {
            form.rule(left, line + 8, right, line + 8, RULE, 1.2f).handwriting(sentence, left + 10, line, 34, PEN);
            line += 68;
        }
        y = line + 30;
        form.text("Requested", left, y, sansBold(22), GREY);
        y = checkboxes(form, left, y + 40, new String[] {"Replacement", "Credit", "Repair", "Return authorisation"}, 0);
        form.text("Urgency", left, y, sansBold(22), GREY);
        y = checkboxes(form, left, y + 40, new String[] {"Normal", "Urgent"}, 1);
        form.handwriting("customers are waiting!", left + 420, y - 44, 32, PEN);
        y = formField(form, "Value", left, y + 10, 520, "4 x 55.00 = 220.00 EUR");
        form.text("Signature", left, y, sansBold(22), GREY)
                .rule(left, y + 70, left + 500, y + 70, RULE, 1.2f)
                .signature(left + 30, y + 60, 260, PEN)
                .handwriting("E. Bakke", left + 320, y + 62, 30, PEN);
        return jpeg("Quality defect.jpg", "Photo of a filled-in form", "NO", form.photographed());
    }

    /** Sørensen Outdoor ApS, Roskilde: an invoice with the price dispute written on it, scanned. */
    private static ExampleDocument pricingDispute() {
        var invoice = DocumentPainter.a4(6);
        invoice.text("NORDIC SUPPLY", MARGIN, 170, sansBold(46), INK)
                .text("Invoice", MARGIN, 222, sans(32), GREY)
                .textRight("INV-2026-11968", A4_WIDTH - MARGIN, 170, sansBold(36), INK)
                .textRight("Invoice date 21 August 2026", A4_WIDTH - MARGIN, 214, sans(24), GREY)
                .textRight("Due 20 September 2026 (30 days)", A4_WIDTH - MARGIN, 250, sans(24), GREY)
                .rule(MARGIN, 290, A4_WIDTH - MARGIN, 290, INK, 2f);
        var detail = sans(25);
        invoice.text("Bill to", MARGIN, 350, sansBold(24), GREY)
                .paragraph(
                        "Sørensen Outdoor ApS\nØstergade 56\n4000 Roskilde\nDenmark\nCustomer no. C-10817"
                                + "  -  VAT DK58316689",
                        MARGIN,
                        390,
                        520,
                        detail,
                        INK);
        invoice.text("References", 700, 350, sansBold(24), GREY)
                .paragraph(
                        "Order SO-2026-006912\nYour reference PO-49898\nShipment SH-2026-007377"
                                + "\nDelivered 24 August 2026\nPayment terms 30 days net",
                        700,
                        390,
                        440,
                        detail,
                        INK);
        int table = 640;
        var header = sansBold(22);
        invoice.rule(MARGIN, table, A4_WIDTH - MARGIN, table, INK, 1.5f)
                .text("Qty", MARGIN, table + 38, header, GREY)
                .text("Description", MARGIN + 60, table + 38, header, GREY)
                .text("SKU", 720, table + 38, header, GREY)
                .textRight("Unit", 940, table + 38, header, GREY)
                .textRight("Disc.", 1000, table + 38, header, GREY)
                .textRight("Net EUR", A4_WIDTH - MARGIN, table + 38, header, GREY)
                .rule(MARGIN, table + 56, A4_WIDTH - MARGIN, table + 56, RULE, 1f);
        String[][] lines = {
            {"48", "Rondane Elv Down Sleeping Bag 0°C Ruby Red", "RON-SLP-0021", "390.50", "5 %", "17 806.80"},
            {"2", "Peilung Bre XT Camp Lantern Midnight Blue", "KOM-NAV-0025", "13.82", "5 %", "26.26"},
            {"30", "Myrsky Nuten Plus Hardshell Jacket L Ruby Red", "MYR-SHL-0007", "418.90", "5 %", "11 938.65"},
            {"1", "Halla Pallas Trekking Poles 150 cm Burnt Orange", "HAL-ACC-0035", "117.50", "5 %", "111.62"},
        };
        int row = table + 100;
        var cell = sans(21);
        invoice.highlight(MARGIN - 10, row - 30, TEXT_WIDTH + 20, 44);
        for (var line : lines) {
            invoice.text(line[0], MARGIN, row, cell, INK)
                    .text(line[1], MARGIN + 60, row, cell, INK)
                    .text(line[2], 720, row, mono(19), INK)
                    .textRight(line[3], 940, row, cell, INK)
                    .textRight(line[4], 1000, row, cell, INK)
                    .textRight(line[5], A4_WIDTH - MARGIN, row, cell, INK)
                    .rule(MARGIN, row + 18, A4_WIDTH - MARGIN, row + 18, RULE, 1f);
            row += 54;
        }
        int totals = row + 30;
        invoice.textRight("Total net", 960, totals, detail, GREY)
                .textRight("29 883.33", A4_WIDTH - MARGIN, totals, detail, INK)
                .textRight("VAT 0 % (reverse charge, intra-EU)", 960, totals + 40, detail, GREY)
                .textRight("0.00", A4_WIDTH - MARGIN, totals + 40, detail, INK)
                .textRight("Total EUR", 960, totals + 90, sansBold(28), INK)
                .textRight("29 883.33", A4_WIDTH - MARGIN, totals + 90, sansBold(28), INK)
                .rule(640, totals + 56, A4_WIDTH - MARGIN, totals + 56, INK, 1.5f);
        int note = totals + 190;
        invoice.handwriting("Agreed price 355.00/pc for the Rondane Elv bags at the", MARGIN, note, 32, PEN)
                .handwriting(
                        "Copenhagen trade fair (offer Q-2026-0417, valid to 30.9.2026).", MARGIN, note + 54, 32, PEN)
                .handwriting("Invoiced 390.50 - 5 % = 370.98 per bag.", MARGIN, note + 108, 32, PEN)
                .handwriting("Difference 15.98 x 48 = 767.04 EUR - please credit.", MARGIN, note + 162, 32, PEN)
                .handwriting("Mads Rasmussen, 3.9.2026", MARGIN, note + 236, 30, PEN)
                .signature(MARGIN + 460, note + 236, 280, PEN);
        invoice.text(
                "Nordic Supply Oy  -  Vantaa, Finland  -  FI12345678  -  billing@nordicsupply.example",
                MARGIN,
                1680,
                sans(20),
                GREY);
        return pdf(
                "Pricing dispute.pdf", "Scanned invoice with a note on it, 1 page", "DK", List.of(invoice.scanned()));
    }

    /** Aapastore OÜ, Tartu: the wrong colour delivered, in a chat with the account manager. */
    private static ExampleDocument wrongColour() {
        var phone = new DocumentPainter(1000, 1880, new Color(0x0b, 0x14, 0x1a), 7);
        phone.box(0, 0, 1000, 60, CHAT_BAR, null, 0)
                .text("09:41", 40, 40, sansBold(24), CHAT_TEXT)
                .textRight("5G  87 %", 960, 40, sans(22), CHAT_TEXT)
                .box(0, 60, 1000, 110, CHAT_BAR, null, 0)
                .text("<", 40, 130, sans(40), CHAT_TEXT)
                .box(100, 80, 70, 70, new Color(0x3b, 0x5b, 0x6a), null, 35)
                .text("KR", 116, 126, sansBold(26), CHAT_TEXT)
                .text("Kadri Rebane  -  Aapastore OÜ", 200, 118, sansBold(28), CHAT_TEXT)
                .text("Store Manager, Tartu  -  online", 200, 152, sans(22), CHAT_FAINT);
        phone.text("Tue 1 Sep 2026", 410, 230, sans(20), CHAT_FAINT);
        int y = 270;
        y = bubble(
                phone,
                "Hi Helmi! We unpacked shipment SH-2026-006922 today (our order SO-2026-006473).",
                y,
                false,
                "10:12");
        y = bubble(
                phone,
                "All 36 Nordvind Skog Pro Insulated Jackets XS came in Ochre. We ordered Petrol"
                        + " (NVD-SHL-0043). Photo:",
                y,
                false,
                "10:13");
        phone.box(60, y, 420, 240, new Color(0x2a, 0x3b, 0x44), null, 18)
                .box(120, y + 40, 300, 160, new Color(0xb8, 0x86, 0x3b), null, 12)
                .text("IMG_4471.jpg", 180, y + 130, sans(22), new Color(0x33, 0x25, 0x10));
        y += 270;
        y = bubble(
                phone,
                "Oh no, sorry about that! I'll open a claim right away. Do you want the Petrol ones"
                        + " sent out, or would you keep the Ochre at a discount?",
                y,
                true,
                "10:16");
        y = bubble(
                phone,
                "We can't sell Ochre, our web shop photos are all Petrol. Please send 36 Petrol and a"
                        + " return label for the Ochre ones.",
                y,
                false,
                "10:18");
        y = bubble(
                phone,
                "Value 36 x 193.90 = 6 980.40 EUR. We need them before our winter campaign starts on" + " 16 October.",
                y,
                false,
                "10:19");
        bubble(phone, "Understood. Claim coming up, you'll get the confirmation by email today.", y, true, "10:21");
        phone.box(0, 1770, 1000, 110, CHAT_BAR, null, 0)
                .box(40, 1792, 820, 66, CHAT_THEIRS, null, 33)
                .text("Message", 76, 1835, sans(26), CHAT_FAINT)
                .box(880, 1792, 66, 66, CHAT_MINE, null, 33);
        return png(
                "Wrong colour.png", "Screenshot of a chat", "EE", phone.page().image());
    }

    /** The header block of an exported email: the fields a mail client prints above the message. */
    private static int emailHeader(
            DocumentPainter page, String from, String to, String date, String subject, String attachments) {
        var label = sansBold(26);
        var value = sans(26);
        String[][] rows = {
            {"From:", from}, {"To:", to}, {"Date:", date}, {"Subject:", subject}, {"Attachments:", attachments},
        };
        int y = 180;
        for (var entry : rows) {
            page.text(entry[0], MARGIN, y, label, GREY).text(entry[1], MARGIN + 220, y, value, INK);
            y += 46;
        }
        page.rule(MARGIN, y - 10, A4_WIDTH - MARGIN, y - 10, RULE, 1.5f);
        return y + 60;
    }

    /**
     * A field of a paper form: its label in small capitals, a ruled line for the answer, and the answer written in
     * by hand. Returns where the next row of fields starts.
     */
    private static int formField(DocumentPainter form, String label, int x, int y, int width, String answer) {
        form.text(label.toUpperCase(), x, y, sansBold(20), GREY)
                .rule(x, y + 56, x + width, y + 56, RULE, 1.2f)
                .handwriting(answer, x + 10, y + 46, 34, PEN);
        return y + 110;
    }

    /** A row of checkboxes with their labels, the one at {@code checked} crossed by hand. */
    private static int checkboxes(DocumentPainter form, int x, int y, String[] labels, int checked) {
        int cursor = x;
        for (int i = 0; i < labels.length; i++) {
            form.box(cursor, y - 24, 30, 30, null, INK, 4);
            if (i == checked) {
                form.cross(cursor, y - 24, 30, PEN);
            }
            form.text(labels[i], cursor + 44, y, sans(24), INK);
            cursor += 44 + form.widthOf(labels[i], sans(24)) + 60;
        }
        return y + 70;
    }

    /** One message of a chat, in a bubble on the writer's side with the time under it. Returns where the next starts. */
    private static int bubble(DocumentPainter phone, String text, int y, boolean mine, String time) {
        Font font = sans(26);
        int width = 640;
        int padding = 28;
        int x = mine ? 1000 - 60 - width - 2 * padding : 60;
        int lines = phone.wrap(text, width, font).size();
        int height = lines * lineHeight(font) + padding + 12;
        phone.box(x, y, width + 2 * padding, height, mine ? CHAT_MINE : CHAT_THEIRS, null, 24);
        phone.paragraph(text, x + padding, y + padding + 20, width, font, CHAT_TEXT);
        phone.textRight(time, x + width + 2 * padding - padding, y + height + 30, sans(20), CHAT_FAINT);
        return y + height + 60;
    }
}
