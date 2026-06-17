package top.nkbe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

public class Metrics {

    private final Plugin plugin;
    private final MetricsBase metricsBase;

    public Metrics(JavaPlugin plugin, int pluginId) {
        this.plugin = plugin;
        // Get the config file
        File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "bStats");
        File configFile = new File(bStatsFolder, "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (!config.isSet("enabled")) {
            config.addDefault("enabled", true);
            config.addDefault("serverUuid", UUID.randomUUID().toString());
            config.addDefault("logFailedRequests", false);
            config.addDefault("logSentData", false);
            config.addDefault("logResponseStatusText", false);
            config.options().header(
                    "bStats (https://bstats.org) collects some basic information for plugin authors, like how\n" +
                            "many servers are using their plugins and their system information.\n" +
                            "All data is available to the public. Check out https://bstats.org to learn more!"
            ).copyDefaults(true);
            try {
                config.save(configFile);
            } catch (IOException ignored) {
            }
        }
        // Load the data
        boolean enabled = config.getBoolean("enabled", true);
        String serverUuid = config.getString("serverUuid");
        boolean logFailedRequests = config.getBoolean("logFailedRequests", false);
        boolean logSentData = config.getBoolean("logSentData", false);
        boolean logResponseStatusText = config.getBoolean("logResponseStatusText", false);
        metricsBase = new MetricsBase(
                "bukkit",
                serverUuid,
                pluginId,
                enabled,
                this::appendPlatformData,
                this::appendServiceData,
                task -> Bukkit.getScheduler().runTask(plugin, task),
                () -> true,
                (message, error) -> this.plugin.getLogger().log(Level.WARNING, message, error),
                (message) -> this.plugin.getLogger().log(Level.INFO, message),
                logFailedRequests,
                logSentData,
                logResponseStatusText
        );
    }

    public void addCustomChart(CustomChart chart) {
        metricsBase.addCustomChart(chart);
    }

    private void appendServiceData(JsonObject data) {
        data.addProperty("pluginVersion", plugin.getDescription().getVersion());
    }

    private void appendPlatformData(JsonObject data) {
        data.addProperty("playerAmount", Bukkit.getOnlinePlayers().size());
        data.addProperty("onlineMode", Bukkit.getOnlineMode() ? 1 : 0);
        data.addProperty("bukkitVersion", Bukkit.getVersion());
        data.addProperty("bukkitName", Bukkit.getName());
        data.addProperty("javaVersion", System.getProperty("java.version"));
        data.addProperty("osName", System.getProperty("os.name"));
        data.addProperty("osArch", System.getProperty("os.arch"));
        data.addProperty("osVersion", System.getProperty("os.version"));
        data.addProperty("coreCount", Runtime.getRuntime().availableProcessors());
    }

    public static class MetricsBase {
        public static final String METRICS_VERSION = "3.0.2";
        private static final String REPORT_URL = "https://bStats.org/api/v2/data/%s";
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        private final String platform;
        private final String serverUuid;
        private final int pluginId;
        private final boolean enabled;
        private final java.util.function.Consumer<JsonObject> appendPlatformDataConsumer;
        private final java.util.function.Consumer<JsonObject> appendServiceDataConsumer;
        private final java.util.function.Consumer<Runnable> submitTaskConsumer;
        private final java.util.function.Supplier<Boolean> checkHasStartFinished;
        private final java.util.function.BiConsumer<String, Throwable> errorLogger;
        private final java.util.function.Consumer<String> infoLogger;
        private final boolean logFailedRequests;
        private final boolean logSentData;
        private final boolean logResponseStatusText;
        private final Set<CustomChart> customCharts = new HashSet<>();

        public MetricsBase(String platform, String serverUuid, int pluginId, boolean enabled,
                           java.util.function.Consumer<JsonObject> appendPlatformDataConsumer,
                           java.util.function.Consumer<JsonObject> appendServiceDataConsumer,
                           java.util.function.Consumer<Runnable> submitTaskConsumer,
                           java.util.function.Supplier<Boolean> checkHasStartFinished,
                           java.util.function.BiConsumer<String, Throwable> errorLogger,
                           java.util.function.Consumer<String> infoLogger,
                           boolean logFailedRequests, boolean logSentData, boolean logResponseStatusText) {
            this.platform = platform;
            this.serverUuid = serverUuid;
            this.pluginId = pluginId;
            this.enabled = enabled;
            this.appendPlatformDataConsumer = appendPlatformDataConsumer;
            this.appendServiceDataConsumer = appendServiceDataConsumer;
            this.submitTaskConsumer = submitTaskConsumer;
            this.checkHasStartFinished = checkHasStartFinished;
            this.errorLogger = errorLogger;
            this.infoLogger = infoLogger;
            this.logFailedRequests = logFailedRequests;
            this.logSentData = logSentData;
            this.logResponseStatusText = logResponseStatusText;
            if (enabled) {
                startSubmitting();
            }
        }

        public void addCustomChart(CustomChart chart) {
            this.customCharts.add(chart);
        }

        private void startSubmitting() {
            final Runnable submitTask = () -> {
                if (!checkHasStartFinished.get()) {
                    scheduler.schedule(this::submitData, 1, TimeUnit.MINUTES);
                    return;
                }
                submitData();
            };
            long initialDelay = (long) (1000 * 60 * (3 + Math.random() * 3));
            long secondDelay = (long) (1000 * 60 * (Math.random() * 30));
            scheduler.schedule(submitTask, initialDelay, TimeUnit.MILLISECONDS);
            scheduler.scheduleAtFixedRate(this::submitData, initialDelay + secondDelay, 1000 * 60 * 30, TimeUnit.MILLISECONDS);
        }

        private void submitData() {
            final JsonObject data = new JsonObject();
            appendPlatformDataConsumer.accept(data);
            final JsonArray serviceData = new JsonArray();
            final JsonObject pluginData = new JsonObject();
            pluginData.addProperty("pluginId", pluginId);
            appendServiceDataConsumer.accept(pluginData);
            final JsonArray customChartsData = new JsonArray();
            for (CustomChart customChart : customCharts) {
                JsonObject chart = customChart.getRequestJsonObject(errorLogger, logFailedRequests);
                if (chart != null) {
                    customChartsData.add(chart);
                }
            }
            pluginData.add("customCharts", customChartsData);
            serviceData.add(pluginData);
            data.add("serviceData", serviceData);
            data.addProperty("serverUuid", serverUuid);
            data.addProperty("metricsVersion", METRICS_VERSION);
            if (logSentData) {
                infoLogger.accept("Sent bStats data: " + data.toString());
            }
            submitTaskConsumer.accept(() -> {
                try {
                    sendData(data);
                } catch (Exception e) {
                    if (logFailedRequests) {
                        errorLogger.accept("Could not submit bStats data!", e);
                    }
                }
            });
        }

        private void sendData(JsonObject data) throws Exception {
            String url = String.format(REPORT_URL, platform);
            HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
            byte[] compressedData = compress(data.toString());
            connection.setRequestMethod("POST");
            connection.addRequestProperty("Accept", "application/json");
            connection.addRequestProperty("Connection", "close");
            connection.addRequestProperty("Content-Encoding", "gzip");
            connection.addRequestProperty("Content-Length", String.valueOf(compressedData.length));
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "Metrics-Service/1");
            connection.setDoOutput(true);
            try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                outputStream.write(compressedData);
            }
            StringBuilder builder = new StringBuilder();
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    builder.append(line);
                }
            }
            if (logResponseStatusText) {
                infoLogger.accept("Sent data to bStats and received response: " + builder.toString());
            }
        }

        private static byte[] compress(final String str) throws IOException {
            if (str == null) {
                return null;
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
                gzip.write(str.getBytes(StandardCharsets.UTF_8));
            }
            return outputStream.toByteArray();
        }
    }

    public abstract static class CustomChart {
        private final String chartId;

        protected CustomChart(String chartId) {
            if (chartId == null) {
                throw new IllegalArgumentException("chartId must not be null");
            }
            this.chartId = chartId;
        }

        public JsonObject getRequestJsonObject(java.util.function.BiConsumer<String, Throwable> errorLogger, boolean logFailedRequests) {
            JsonObject chart = new JsonObject();
            chart.addProperty("chartId", chartId);
            try {
                JsonObject data = getChartData();
                if (data == null) {
                    return null;
                }
                chart.add("data", data);
            } catch (Exception e) {
                if (logFailedRequests) {
                    errorLogger.accept("Failed to get data for custom chart with id " + chartId, e);
                }
                return null;
            }
            return chart;
        }

        protected abstract JsonObject getChartData() throws Exception;
    }

    public static class SimplePie extends CustomChart {
        private final Callable<String> callable;

        public SimplePie(String chartId, Callable<String> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            String value = callable.call();
            if (value == null || value.isEmpty()) {
                return null;
            }
            JsonObject data = new JsonObject();
            data.addProperty("value", value);
            return data;
        }
    }
}
