package com.example.mcpserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service untuk menyediakan Tools Analisis Pembayaran kepada Spring AI.
 * Implementasi logika bisnis menggunakan data yang dimuat dari data_smire_final.json.
 */
@Service
public class PaymentsAnalyticsToolService {

    private final List<Map<String, Object>> smireData;

    @Autowired
    public PaymentsAnalyticsToolService(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.smireData = loadSmireData(resourceLoader, objectMapper);

        if (this.smireData.isEmpty()) {
            System.err.println("PERINGATAN: data_smire_final.json gagal dimuat atau kosong. Tools akan mengembalikan hasil placeholder.");
        } else {
            // Log total baris
            System.out.println("INFO: data_smire_final.json berhasil dimuat. Total baris: " + this.smireData.size());

            // LOGGING DEBUG BARU: Tampilkan baris pertama
            Map<String, Object> firstEntry = this.smireData.get(0);
            System.out.println("DEBUG DUMP: Entry data pertama (Indeks 0) adalah: " + firstEntry);

            // Logika untuk menampilkan nilai TPV/TPT dari baris pertama (Tambahan opsional)
            if (firstEntry.containsKey("tpv") && firstEntry.containsKey("tpt")) {
                System.out.println("DEBUG DUMP: Nilai TPV baris pertama: " + firstEntry.get("tpv") +
                        " | Nilai TPT baris pertama: " + firstEntry.get("tpt"));
            }
        }
    }

    /**
     * Memuat dan mengurai data JSON dari classpath (src/main/resources/data/data_smire_final.json).
     */
    private List<Map<String, Object>> loadSmireData(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        try {
            // Path relatif terhadap src/main/resources/
            Resource resource = resourceLoader.getResource("classpath:data/data_smire_final.json");
            try (InputStream is = resource.getInputStream()) {
                return objectMapper.readValue(is, List.class);
            }
        } catch (IOException e) {
            System.err.println("FATAL ERROR: Gagal memuat data_smire_final.json dari 'classpath:data/': " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ----------------------------------------------------------------------
    // HELPER METHODS
    // ----------------------------------------------------------------------

    private void ensureYyyyMm(String month) {
        if (month != null && !month.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("month must be in YYYY-MM format, e.g. 2025-07");
        }
    }

    private String resolveMonth(String month) {
        if (month != null) {
            ensureYyyyMm(month);
            return month;
        }

        if (!this.smireData.isEmpty()) {
            return this.smireData.stream()
                    .map(item -> (String) item.get("month")) // Asumsi ada kolom 'month'
                    .max(String::compareTo)
                    .orElse("jun 2025");
        }

        return "jun 2025 (Fallback)";
    }

    /**
     * Mengkonversi nilai TPV/TPT (yang berbentuk String dengan koma) menjadi Long.
     */
    private long parseNumericValue(Object value) {
        if (value == null) return 0L;

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        String strValue = String.valueOf(value);
        try {
            String cleanValue = strValue.replace(",", "").replace(" ", "").trim();
            if (cleanValue.isEmpty() || cleanValue.equalsIgnoreCase("null")) {
                return 0L;
            }
            return Long.parseLong(cleanValue);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Helper utama untuk memfilter data berdasarkan month, product_type, dan brand_id.
     */
    private List<Map<String, Object>> filterData(String month, String product, String brand_id) {
        String resolvedMonth = resolveMonth(month);

        return this.smireData.stream()
                .filter(item -> {
                    boolean monthMatch = resolvedMonth.equalsIgnoreCase((String) item.get("month"));

                    // PERBAIKAN PENTING: Menggunakan 'product_type' sebagai kolom data
                    boolean productMatch = (product == null || product.equalsIgnoreCase((String) item.get("product_type")));

                    boolean brandIdMatch = (brand_id == null || brand_id.equalsIgnoreCase((String) item.get("brand_id")));

                    return monthMatch && productMatch && brandIdMatch;
                })
                .collect(Collectors.toList());
    }


    // ----------------------------------------------------------------------
    // TOOL IMPLEMENTATIONS
    // ----------------------------------------------------------------------

    // =========================
    // Tool: get_welcome_message
    // =========================
    @Tool(description = "Mengembalikan pesan sapaan saat user pertama kali menyapa atau meminta bantuan. Gunakan ini jika user mengetik 'halo', 'hi', atau 'bantuan'.")
    public String get_welcome_message() {
        return "Halo! 👋\n" +
                "Selamat datang di SMIRE (Smart Merchant Insight & Recommendation Engine).\n\n" +
                "Saya di sini untuk membantu Anda mendapatkan insight dan rekomendasi cerdas tentang perkembangan merchant DOKU.\n\n" +
                "Melalui WhatsApp ini, Anda bisa dengan mudah:\n" +
                "✅ Melihat status dan performa merchant Anda\n" +
                "✅ Menerima insight dan rekomendasi untuk meningkatkan pertumbuhan\n" +
                "✅ Mengakses laporan singkat terkait growth merchant\n \n" +
                "Silahkan bertanya untuk mendapatkan insight:\n";
    }

    // =========================
    // Tool: get_churn_prediction_analysis
    // =========================
    @Tool(description = "Menganalisis potensi churn merchant pada bulan tertentu, mengembalikan ringkasan kategori performa dan daftar merchant berpotensi churn.")
    public Map<String, Object> get_churn_prediction_analysis(
            String month // Optional[str]
    ) {
        String resolvedMonth = resolveMonth(month);
        List<Map<String, Object>> filteredList = filterData(month, null, null); // Tidak ada filter produk

        long totalMerchants = filteredList.stream()
                .map(item -> (String) item.get("brand_id"))
                .distinct()
                .count();

        Map<String, Long> summary = filteredList.stream()
                .filter(item -> item.get("Churn_Prediction") != null)
                .collect(Collectors.groupingBy(
                        item -> (String) item.get("Churn_Prediction"),
                        Collectors.counting()
                ));

        return Map.of(
                "metric", "Merchant Churn Potential Analysis",
                "filters", Map.of("month", resolvedMonth),
                "Total_Merchant_Count", totalMerchants,
                "Summary", summary,
                "Potentially_Churning_Merchants", filteredList.stream()
                        .filter(item -> "Critical Risk".equals(item.get("Churn_Prediction")))
                        .map(item -> Map.of("brand_id", item.get("brand_id"), "Churn_Prediction", item.get("Churn_Prediction")))
                        .limit(5)
                        .collect(Collectors.toList())
        );
    }

    // =========================
    // Tool: get_churn_candidates
    // =========================
    @Tool(description = "Mengidentifikasi brand_id merchant yang berpotensi churn atau sudah churn; filters: month, product")
    public Map<String, Object> get_churn_candidates(
            String month, // Optional[str]
            String product // Optional[str] -> Diteruskan sebagai filter product_type
    ) {
        String resolvedMonth = resolveMonth(month);

        List<String> churnCandidates = filterData(month, product, null).stream()
                .filter(item -> "RISK".equalsIgnoreCase((String) item.get("Churn_Status")))
                .map(item -> (String) item.get("brand_id"))
                .distinct()
                .collect(Collectors.toList());

        return Map.of(
                "metric", "Churn Candidates",
                "filters", Map.of("month", resolvedMonth, "product", product),
                "total_candidates", churnCandidates.size(),
                "brand_ids", churnCandidates
        );
    }

    // =========================
    // Tool: calculate_profit_total
    // =========================
    @Tool(description = "Menghitung total TPV dari transaksi berlabel 'profit' (dianggap profit); filters: month, product, brand_id")
    public Map<String, Object> calculate_profit_total(
            String month,     // Optional[str]
            String product,   // Optional[str] -> Diteruskan sebagai filter product_type
            String brand_id   // Optional[str]
    ) {
        List<Map<String, Object>> filteredList = filterData(month, product, brand_id);

        long grandTotal = filteredList.stream()
                .filter(item -> "PROFIT".equalsIgnoreCase((String) item.get("Transaction_Type")))
                .mapToLong(item -> parseNumericValue(item.get("tpv")))
                .sum();

        return Map.of(
                "metric", "Profit TPV",
                "filters", Map.of("month", resolveMonth(month), "product", product, "brand_id", brand_id),
                "grand_total", grandTotal
        );
    }

    // =========================
    // Tool: calculate_overall_metrics
    // =========================
    @Tool(description = "Menghitung total TPT dan TPV (gabungan dari data churn & profit); filters: month, product, brand_id")
    public Map<String, Object> calculate_overall_metrics(
            String month,     // Optional[str]
            String product,   // Optional[str] -> Diteruskan sebagai filter product_type
            String brand_id   // Optional[str]
    ) {
        List<Map<String, Object>> filteredList = filterData(month, product, brand_id);

        long totalTpt = filteredList.stream()
                .mapToLong(item -> parseNumericValue(item.get("tpt")))
                .sum();

        long totalTpv = filteredList.stream()
                .mapToLong(item -> parseNumericValue(item.get("tpv")))
                .sum();

        return Map.of(
                "metric", "Overall Metrics",
                "filters", Map.of("month", resolveMonth(month), "product", product, "brand_id", brand_id),
                "total_tpt", totalTpt,
                "total_tpv", totalTpv
        );
    }

    // =========================
    // Tool: get_product_mix_contribution
    // =========================
    @Tool(description = "Menghitung kontribusi (persentase) TPV dan TPT dari setiap 'product_type' di bulan tertentu.")
    public Map<String, Object> get_product_mix_contribution(String month) {
        String resolvedMonth = resolveMonth(month);
        List<Map<String, Object>> filteredList = filterData(month, null, null); // Tidak ada filter produk

        long grandTotalTpv = filteredList.stream()
                .mapToLong(item -> parseNumericValue(item.get("tpv")))
                .sum();

        Map<String, Map<String, Object>> mixByProduct = filteredList.stream()
                .collect(Collectors.groupingBy(
                        item -> (String) item.get("product_type"),
                        Collectors.collectingAndThen(
                                Collectors.summingLong(item -> parseNumericValue(item.get("tpv"))),
                                totalTpvPerProduct -> {
                                    double pct = (grandTotalTpv > 0) ?
                                            (double) totalTpvPerProduct / grandTotalTpv * 100.0 : 0.0;
                                    return Map.of(
                                            "TotalTPV", totalTpvPerProduct,
                                            "TPV_Pct", String.format("%.2f%%", pct)
                                    );
                                }
                        )
                ));

        return Map.of(
                "metric", "Product Mix Contribution",
                "filters", Map.of("month", resolvedMonth),
                "GrandTotalTPV", grandTotalTpv,
                "mix_by_product", mixByProduct
        );
    }

    // =========================
    // Tool: get_monthly_change
    // =========================
    @Tool(description = "Menghitung perubahan persentase (Growth/Decline) TPV/TPT antara dua bulan (month_a ke month_b); filters: product, brand_id")
    public Map<String, Object> get_monthly_change(
            String month_a, // str
            String month_b, // str
            String product,   // Optional[str] -> Diteruskan sebagai filter product_type
            String brand_id   // Optional[str]
    ) {
        ensureYyyyMm(month_a);
        ensureYyyyMm(month_b);
        // Implementasikan logika perbandingan bulan di sini, menggunakan filterData(month_a, product, brand_id) dan filterData(month_b, product, brand_id)
        return Map.of("metric", "Monthly Change (" + month_a + " -> " + month_b + ")", "filters", Map.of("product", product, "brand_id", brand_id), "TpvGrowthPct", "0.00%");
    }

    // =========================
    // Tool: get_merchant_recommendation (Placeholder)
    // =========================
    // Tool ini tidak menerima filter product
    @Tool(description = "Menerbitkan rekomendasi aksi (Upsell, Cross-sell, Promotion, Reactivation) untuk semua merchant di bulan tertentu.")
    public Map<String, Object> get_merchant_recommendation(String month) {
        String resolvedMonth = resolveMonth(month);
        return Map.of("metric", "Merchant Recommendation Engine", "filters", Map.of("month", resolvedMonth), "Summary", Map.of("Upsell", 10, "Cross-sell", 20), "Recommendation_List", List.of());
    }

    // =========================
    // Tool: get_product_mix (Placeholder)
    // =========================
    @Tool(description = "Menghitung kontribusi (persentase) TPV dan TPT dari setiap produk di bulan tertentu; filter: month")
    public Map<String, Object> get_product_mix(String month) {
        if (month != null) ensureYyyyMm(month);
        // Tool ini fungsionalitasnya mirip dengan get_product_mix_contribution dan tidak menerima filter product
        return Map.of("metric", "Product Mix Contribution", "filters", Map.of("month", resolveMonth(month)), "GrandTotalTPV", 0L, "mix_by_product", Map.of());
    }
}