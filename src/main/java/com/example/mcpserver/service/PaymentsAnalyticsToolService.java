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

// ... (Bagian Javadoc)

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
        }
    }

    // =========================
    // Private Utility Methods
    // =========================

    // Metode untuk memuat data dari JSON
    private List<Map<String, Object>> loadSmireData(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/data_smire_final.json");

            // Cek apakah resource benar-benar ada (Tambahan Debugging)
            if (!resource.exists()) {
                throw new IOException("File data_smire_final.json tidak ditemukan di classpath.");
            }

            try (InputStream is = resource.getInputStream()) {
                List<Map<String, Object>> data = objectMapper.readValue(is, objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

                // Cek apakah data kosong (Tambahan Debugging)
                if (data.isEmpty()) {
                    System.err.println("PERINGATAN: data_smire_final.json ditemukan, tetapi kontennya kosong atau tidak valid.");
                }

                return data;
            }
        } catch (IOException e) {
            // Error ini akan menangkap jika file tidak ada atau gagal dibaca/parse
            System.err.println("Gagal memuat data_smire_final.json: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ... (sisa utility methods dan tool methods lainnya)

    // Utility untuk menyesuaikan format bulan (asumsi input Oct-24)
    private String resolveMonth(String month) {
        // Mengembalikan nilai bulan apa adanya karena format data sudah Oct-24
        return month;
    }

    // Utility untuk validasi format bulan (placeholder)
    private void ensureYyyyMm(String month) {
        // Logika validasi bulan dapat ditambahkan di sini
    }

    // Utility untuk membersihkan string angka dan mengonversinya menjadi Double
    private double cleanAndParseNumber(Object value) {
        if (value == null) {
            return 0.0;
        }
        // Menghapus koma (misalnya "1,000,000" menjadi "1000000")
        String s = value.toString().replace(",", "").trim();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // Utility untuk memfilter data berdasarkan semua kriteria
    private List<Map<String, Object>> filterData(String month, String pillar, String product_type, String brand_id, String merchant_name) {
        if (this.smireData == null || this.smireData.isEmpty()) {
            return Collections.emptyList();
        }

        return this.smireData.stream().filter(entry -> {
            boolean monthMatch = (month == null || resolveMonth(month).equals(entry.get("month")));
            // PERUBAHAN: pillar dan product_type diizinkan null atau kosong untuk kebutuhan grouping
            boolean pillarMatch = (pillar == null || pillar.isEmpty() || pillar.equals(entry.get("pillar")));
            boolean productMatch = (product_type == null || product_type.isEmpty() || product_type.equals(entry.get("product_type")));
            boolean brandIdMatch = (brand_id == null || brand_id.isEmpty() || brand_id.equals(entry.get("brand_id")));

            // Filter berdasarkan merchant_name (case-insensitive)
            boolean merchantNameMatch = (merchant_name == null || merchant_name.isEmpty() ||
                    (entry.get("merchant_name") != null && merchant_name.equalsIgnoreCase(entry.get("merchant_name").toString())));

            return monthMatch && pillarMatch && productMatch && brandIdMatch && merchantNameMatch;

        }).collect(Collectors.toList());
    }

    // =========================
    // Tool: get_summary_analytics
    // =========================
    @Tool(description = "Meringkas total TPV dan TPT berdasarkan filter: month (Wajib, format Oct-24), pillar, product_type, brand_id, dan merchant_name.")
    public Map<String, Object> get_summary_analytics(
            String month,             // Wajib (e.g., "Oct-24")
            String pillar,            // Optional
            String product_type,      // Optional
            String brand_id,          // Optional
            String merchant_name      // Optional merchant_name
    ) {
        String resolvedMonth = resolveMonth(month);
        List<Map<String, Object>> filteredData = filterData(resolvedMonth, pillar, product_type, brand_id, merchant_name);

        double totalTpv = filteredData.stream()
                .mapToDouble(entry -> cleanAndParseNumber(entry.get("tpv")))
                .sum();

        double totalTpt = filteredData.stream()
                .mapToDouble(entry -> cleanAndParseNumber(entry.get("tpt")))
                .sum();

        return Map.of(
                "metric", "Monthly Summary for " + resolvedMonth,
                "filters", Map.of(
                        "month", resolvedMonth,
                        "pillar", (pillar != null ? pillar : ""),
                        "product_type", (product_type != null ? product_type : ""),
                        "brand_id", (brand_id != null ? brand_id : ""),
                        "merchant_name", (merchant_name != null ? merchant_name : "")
                ),
                "Total_TPV", totalTpv,
                "Total_TPT", (long) totalTpt
        );
    }

    // =========================
    // Tool: get_monthly_growth
    // =========================
    @Tool(description = "Menghitung persentase pertumbuhan TPV antara dua bulan. Filter: month_a (bulan awal), month_b (bulan akhir), pillar, product_type, brand_id, dan merchant_name. Catatan: month harus dalam format Oct-24.")
    public Map<String, Object> get_monthly_growth(
            String month_a,            // Wajib (Bulan awal, e.g., "Oct-24")
            String month_b,            // Wajib (Bulan akhir, e.g., "Nov-24")
            String pillar,             // Optional[str]
            String product_type,       // Optional[str]
            String brand_id,           // Optional[str]
            String merchant_name       // Optional[str]
    ) {
        ensureYyyyMm(month_a);
        ensureYyyyMm(month_b);

        // Filter data dan hitung TPV untuk Bulan A
        List<Map<String, Object>> dataA = filterData(resolveMonth(month_a), pillar, product_type, brand_id, merchant_name);
        double totalTpvA = dataA.stream()
                .mapToDouble(entry -> cleanAndParseNumber(entry.get("tpv")))
                .sum();

        // Filter data dan hitung TPV untuk Bulan B
        List<Map<String, Object>> dataB = filterData(resolveMonth(month_b), pillar, product_type, brand_id, merchant_name);
        double totalTpvB = dataB.stream()
                .mapToDouble(entry -> cleanAndParseNumber(entry.get("tpv")))
                .sum();

        String growthPct;

        if (totalTpvA > 0) {
            double growth = ((totalTpvB - totalTpvA) / totalTpvA) * 100;
            growthPct = String.format("%.2f%%", growth);
        } else if (totalTpvA == 0 && totalTpvB > 0) {
            growthPct = "Inf";
        } else {
            growthPct = "0.00%";
        }

        return Map.of(
                "metric", "Monthly TPV Growth (" + month_a + " -> " + month_b + ")",
                "filters", Map.of(
                        "month_a", resolveMonth(month_a),
                        "month_b", resolveMonth(month_b),
                        "pillar", (pillar != null ? pillar : ""),
                        "product_type", (product_type != null ? product_type : ""),
                        "brand_id", (brand_id != null ? brand_id : ""),
                        "merchant_name", (merchant_name != null ? merchant_name : "")
                ),
                "TPV_A", totalTpvA,
                "TPV_B", totalTpvB,
                "TpvGrowthPct", growthPct
        );
    }

    // =========================
    // Tool: get_merchant_recommendation (Placeholder)
    // =========================
    @Tool(description = "Menerbitkan rekomendasi aksi (Upsell, Cross-sell, Promotion, Reactivation) untuk semua merchant di bulan tertentu.")
    public Map<String, Object> get_merchant_recommendation(String month) {
        String resolvedMonth = resolveMonth(month);
        // Placeholder implementasi
        return Map.of("metric", "Merchant Recommendation Engine", "filters", Map.of("month", resolvedMonth), "Summary", Map.of("Upsell", 10, "Cross-sell", 20), "Recommendation_List", List.of());
    }

    // =========================
    // Tool: get_product_mix
    // =========================
    @Tool(description = "Menghitung kontribusi (persentase) TPV dan TPT dari setiap produk di bulan tertentu. Filter: month (Wajib), pillar, brand_id, merchant_name.")
    public Map<String, Object> get_product_mix(
            String month,             // Wajib (e.g., "Oct-24")
            String pillar,            // Optional
            String brand_id,          // Optional
            String merchant_name      // Optional merchant_name
    ) {
        if (month != null) ensureYyyyMm(month);

        // Catatan: product_type dibuat null di filterData karena kita ingin menghitung mix-nya
        List<Map<String, Object>> filteredData = filterData(resolveMonth(month), pillar, null, brand_id, merchant_name);

        // Agregasi berdasarkan product_type
        Map<String, List<Map<String, Object>>> dataByProduct = filteredData.stream()
                .collect(Collectors.groupingBy(entry -> (String) entry.getOrDefault("product_type", "Unknown")));

        double totalTpvAll = filteredData.stream()
                .mapToDouble(entry -> cleanAndParseNumber(entry.get("tpv")))
                .sum();

        Map<String, Map<String, Object>> mixResult = dataByProduct.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            double productTpv = entry.getValue().stream()
                                    .mapToDouble(e -> cleanAndParseNumber(e.get("tpv")))
                                    .sum();
                            double productTpt = entry.getValue().stream()
                                    .mapToDouble(e -> cleanAndParseNumber(e.get("tpt")))
                                    .sum();

                            // Hitung kontribusi TPV
                            String tpvPct = (totalTpvAll > 0) ? String.format("%.2f%%", (productTpv / totalTpvAll) * 100) : "0.00%";

                            return Map.of(
                                    "TPV_Value", productTpv,
                                    "TPT_Value", (long) productTpt,
                                    "TPV_Contribution_Pct", tpvPct
                            );
                        }
                ));

        return Map.of(
                "metric", "Product Mix for " + resolveMonth(month),
                "filters", Map.of(
                        "month", resolveMonth(month),
                        "pillar", (pillar != null ? pillar : ""),
                        "brand_id", (brand_id != null ? brand_id : ""),
                        "merchant_name", (merchant_name != null ? merchant_name : "")
                ),
                "Total_TPV_All", totalTpvAll,
                "Product_Mix", mixResult
        );
    }

    // =========================
    // Tool: get_data_by_pillar
    // =========================
    @Tool(description = "Menghitung total TPV dan TPT yang dikelompokkan berdasarkan 'pillar'. Filter: month (Wajib), brand_id, product_type, merchant_name.")
    public Map<String, Object> get_data_by_pillar(
            String month,             // Wajib (e.g., "Oct-24")
            String brand_id,          // Optional
            String product_type,      // Optional
            String merchant_name      // Optional
    ) {
        if (month != null) ensureYyyyMm(month);

        // Catatan: pillar dibuat null di filterData karena kita ingin mengelompokkan berdasarkan pillar
        List<Map<String, Object>> filteredData = filterData(resolveMonth(month), null, product_type, brand_id, merchant_name);

        // Agregasi berdasarkan pillar
        Map<String, List<Map<String, Object>>> dataByPillar = filteredData.stream()
                .collect(Collectors.groupingBy(entry -> (String) entry.getOrDefault("pillar", "Unknown")));

        Map<String, Map<String, Object>> result = dataByPillar.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            double pillarTpv = entry.getValue().stream()
                                    .mapToDouble(e -> cleanAndParseNumber(e.get("tpv")))
                                    .sum();
                            double pillarTpt = entry.getValue().stream()
                                    .mapToDouble(e -> cleanAndParseNumber(e.get("tpt")))
                                    .sum();

                            return Map.of(
                                    "TPV_Value", pillarTpv,
                                    "TPT_Value", (long) pillarTpt
                            );
                        }
                ));

        return Map.of(
                "metric", "Data Grouped By Pillar for " + resolveMonth(month),
                "filters", Map.of(
                        "month", resolveMonth(month),
                        "product_type", (product_type != null ? product_type : ""),
                        "brand_id", (brand_id != null ? brand_id : ""),
                        "merchant_name", (merchant_name != null ? merchant_name : "")
                ),
                "Data_By_Pillar", result
        );
    }

    // =========================
    // Tool: get_data_by_product_type
    // =========================
    @Tool(description = "Menghitung total TPV dan TPT yang dikelompokkan berdasarkan 'product_type'. Filter: month (Wajib), pillar, brand_id, merchant_name.")
    public Map<String, Object> get_data_by_product_type(
            String month,             // Wajib (e.g., "Oct-24")
            String pillar,            // Optional
            String brand_id,          // Optional
            String merchant_name      // Optional
    ) {
        if (month != null) ensureYyyyMm(month);

        // Catatan: product_type dibuat null di filterData karena kita ingin mengelompokkan berdasarkan product_type
        List<Map<String, Object>> filteredData = filterData(resolveMonth(month), pillar, null, brand_id, merchant_name);

        // Agregasi berdasarkan product_type
        Map<String, List<Map<String, Object>>> dataByProductType = filteredData.stream()
                .collect(Collectors.groupingBy(entry -> (String) entry.getOrDefault("product_type", "Unknown")));

        Map<String, Map<String, Object>> result = dataByProductType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            double productTpv = entry.getValue().stream()
                                    .mapToDouble(e -> cleanAndParseNumber(e.get("tpv")))
                                    .sum();
                            double productTpt = entry.getValue().stream()
                                    .mapToDouble(e -> cleanAndParseNumber(e.get("tpt")))
                                    .sum();

                            return Map.of(
                                    "TPV_Value", productTpv,
                                    "TPT_Value", (long) productTpt
                            );
                        }
                ));

        return Map.of(
                "metric", "Data Grouped By Product Type for " + resolveMonth(month),
                "filters", Map.of(
                        "month", resolveMonth(month),
                        "pillar", (pillar != null ? pillar : ""),
                        "brand_id", (brand_id != null ? brand_id : ""),
                        "merchant_name", (merchant_name != null ? merchant_name : "")
                ),
                "Data_By_Product_Type", result
        );
    }
}