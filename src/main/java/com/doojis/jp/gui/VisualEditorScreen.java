package com.doojis.jp.gui;

import com.doojis.jp.ReloadListener;
import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class VisualEditorScreen extends Screen {
    private String updateMessage = "Version " + modVersion;

    private Boolean isComp = false;

    private static String modVersion = "1.0.3";

    private final MinecraftClient client;
    private final ResourcePackManager resourcePackManager;
    private final File resourcePackFolder;
    private List<String> categories;
    private int currentCategoryIndex;
    private int currentIdIndex;

    private CheckboxWidget showWeatherCheckbox;
    private CheckboxWidget showClockCheckbox;
    private CheckboxWidget[] hideRowCheckboxes = new CheckboxWidget[4];
    private TextFieldWidget idTextField;
    private TextFieldWidget backgroundTextField;
    private TextFieldWidget colorTextField;

    private JsonObject jsonObject;

    public VisualEditorScreen(MinecraftClient client) {
        super(Text.of("PIDS Visual Editor"));
        this.client = client;
        this.resourcePackManager = new ReloadListener(client, ResourceType.CLIENT_RESOURCES);
        this.resourcePackFolder = new File(this.client.runDirectory, "resourcepacks");
        this.categories = new ArrayList<>();
        this.currentCategoryIndex = 0;
        this.currentIdIndex = 0;
        initializeCategories();
        loadJSON();
    }

    private void reloadJSON() {
        loadJSON();
        currentIdIndex = 0;
        updateFieldsBasedOnCurrentId();
    }

    private void renderBackgroundImage(MatrixStack matrices, JsonObject currentIdObject) {
        JsonElement backgroundElement = jsonObject.getAsJsonArray("pids_images").get(currentIdIndex).getAsJsonObject()
                .get("background");

        String backgroundPath = (backgroundElement != null && !backgroundElement.isJsonNull())
                ? backgroundElement.getAsString()
                : "jsblock:missing";

        if (backgroundPath == null || backgroundPath.trim().isEmpty()) {
            backgroundPath = "jsblock:missing";
        }

        String[] pathComponents = backgroundPath.split(":")[1].split("/");

        String namespace = "jsblock";
        StringBuilder pathBuilder = new StringBuilder();
        for (int i = 0; i < pathComponents.length; i++) {
            pathBuilder.append(pathComponents[i]);
            if (i < pathComponents.length - 1) {
                pathBuilder.append("/");
            }
        }
        String path = pathBuilder.toString().toLowerCase();

        Identifier backgroundIdentifier = new Identifier(namespace, path);

        RenderSystem.setShaderTexture(0, backgroundIdentifier);

        int screenWidth = this.width;
        int screenHeight = this.height;

        int imageWidth = 1920;
        int imageHeight = 1080;
        double aspectRatio = (double) imageWidth / (double) imageHeight;
        int scaledWidth = (int) (screenHeight * aspectRatio * 0.3);
        int scaledHeight = (int) (scaledWidth / aspectRatio);

        int x = (screenWidth / 2) + ((screenWidth / 2) - scaledWidth) / 2;
        int y = (screenHeight - scaledHeight) / 2;

        DrawableHelper.drawTexture(matrices, x, y, 0, 0, scaledWidth, scaledHeight, scaledWidth, scaledHeight);
        String previewText = "Preview";
        int textWidth = this.textRenderer.getWidth(previewText);
        int textX = x + (scaledWidth - textWidth) / 2;
        int textY = y - 20;

        drawStringWithShadow(matrices, this.textRenderer, previewText, textX, textY, 0xFFFFFF);

        if (currentIdObject.has("showWeather") && currentIdObject.get("showWeather").getAsBoolean()) {

            renderIcon(matrices, "weather.png", x, y, scaledWidth, scaledHeight);
        }

        if (currentIdObject.has("showClock") && currentIdObject.get("showClock").getAsBoolean()) {

            renderIcon(matrices, "clock.png", x, y, scaledWidth, scaledHeight);
        }

        JsonElement hideRowElement = currentIdObject.get("hideRow");
        if (hideRowElement != null && !hideRowElement.isJsonNull()) {
            JsonArray hideRowArray = hideRowElement.getAsJsonArray();

            for (int i = 0; i < hideRowArray.size(); i++) {
                boolean hideRow = hideRowArray.get(i).getAsBoolean();
                if (!hideRow) {
                    renderIcon(matrices, "line" + (i + 1) + ".png", x, y, scaledWidth, scaledHeight);
                }
            }
        }
    }

    private void renderIcon(MatrixStack matrices, String iconName, int backgroundX, int backgroundY, int scaledWidth,
            int scaledHeight) {
        Identifier iconIdentifier = new Identifier("doojisjp", "textures/" + iconName);
        RenderSystem.setShaderTexture(0, iconIdentifier);
        int iconX = backgroundX;
        int iconY = backgroundY;
        DrawableHelper.drawTexture(matrices, iconX, iconY, 0, 0, scaledWidth, scaledHeight, scaledWidth, scaledHeight);
    }

    private void initializeCategories() {
        ResourcePackManager resourcePackManager = client.getResourcePackManager();
        Collection<String> activePackNames = resourcePackManager.getEnabledNames();

        File[] files = resourcePackFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {

                    File jsonFile = new File(file, "assets/jsblock/joban_custom_resources.json");
                    if (jsonFile.exists() && activePackNames.contains("file/" + file.getName())) {
                        categories.add(file.getName());
                    }
                } else if (file.isFile() && file.getName().endsWith(".zip")) {

                    if (isZipContainsFile(file, "assets/jsblock/joban_custom_resources.json")
                            && activePackNames.contains("file/" + file.getName())) {
                        categories.add(file.getName());
                    }
                }
            }
        }

        if (categories.isEmpty()) {
            categories.add("No compatible resource packs are loaded");
        }
    }

    public void RL() {
        initializeCategories();
        loadJSON();
    }

    private boolean isZipContainsFile(File zipFile, String filePath) {
        try (ZipFile zf = new ZipFile(zipFile)) {
            ZipEntry entry = zf.getEntry(filePath);
            return entry != null;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void loadJSON() {
        try {
            String categoryName = categories.get(currentCategoryIndex);
            File jsonFile = new File(resourcePackFolder + File.separator + categoryName + File.separator + "assets" +
                    File.separator + "jsblock" + File.separator + "joban_custom_resources.json");
            if (jsonFile.exists()) {
                Gson gson = new Gson();
                JsonParser parser = new JsonParser();
                JsonElement jsonElement = parser.parse(new FileReader(jsonFile));
                if (jsonElement.isJsonObject()) {
                    jsonObject = jsonElement.getAsJsonObject();

                    if (!jsonObject.has("pids_images")) {
                        jsonObject.add("pids_images", new JsonArray());
                    }
                }
            } else {
                Gson gson = new Gson();
                String defaultJsonString = "{\n" +
                        "  \"pids_images\": [\n" +
                        "    {\n" +
                        "      \"id\": \" \",\n" +
                        "      \"showWeather\": false,\n" +
                        "      \"showClock\": false,\n" +
                        "      \"hideRow\": [\n" +
                        "        false,\n" +
                        "        false,\n" +
                        "        false,\n" +
                        "        false\n" +
                        "      ],\n" +
                        "      \"customTextPushArrival\": true,\n" +
                        "      \"background\": \" \",\n" +
                        "      \"color\": \" \"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}";
                jsonObject = gson.fromJson(defaultJsonString, JsonObject.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveJSON() {
        try {
            String categoryName = categories.get(currentCategoryIndex);
            File jsonFile = new File(resourcePackFolder + File.separator + categoryName + File.separator + "assets" +
                    File.separator + "jsblock" + File.separator + "joban_custom_resources.json");
            if (!jsonFile.exists()) {
                jsonFile.createNewFile();
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            FileWriter writer = new FileWriter(jsonFile);
            gson.toJson(jsonObject, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void init() {
        super.init();

        if (categories.isEmpty() || categories.get(0).equals("No compatible resource packs are loaded")) {
            this.addDrawableChild(
                    new ButtonWidget(this.width / 2 - 100, this.height - 30, 200, 20, Text.of("Back"), button -> {
                        this.client.setScreen(null);
                    }));

            isComp = false;

            checkForModUpdates();

        } else {

            isComp = true;

            this.addDrawableChild(new ButtonWidget(10, 10, 50, 20, Text.of("<"), button -> {
                currentCategoryIndex = (currentCategoryIndex - 1 + categories.size()) % categories.size();
                reloadJSON();
            }));
            this.addDrawableChild(new ButtonWidget(this.width - 60, 10, 50, 20, Text.of(">"), button -> {
                currentCategoryIndex = (currentCategoryIndex + 1) % categories.size();
                reloadJSON();
            }));

            this.addDrawableChild(
                    new ButtonWidget(this.width / 2 - 100, this.height - 30, 200, 20, Text.of("Back"), button -> {
                        saveJSON();
                        this.client.setScreen(null);
                    }));

            idTextField = new TextFieldWidget(client.textRenderer, 20, this.height / 6 + 20, 200,
                    20, Text.of(""));
            this.addSelectableChild(idTextField);
            idTextField.setMaxLength(Integer.MAX_VALUE);
            idTextField.setText(jsonObject.has("pids_images")
                    ? jsonObject.getAsJsonArray("pids_images").get(currentIdIndex).getAsJsonObject().get("id")
                            .getAsString()
                    : "");

            String backgroundValue = "";
            String colorValue = "";
            boolean showClockValue = false;
            boolean showWeatherValue = false;
            JsonArray hideRowValue = new JsonArray();

            JsonArray pidsImages = jsonObject.getAsJsonArray("pids_images");
            if (pidsImages != null && currentIdIndex >= 0 && currentIdIndex < pidsImages.size()) {
                JsonObject currentIdObject = pidsImages.get(currentIdIndex).getAsJsonObject();

                JsonElement backgroundElement = currentIdObject.get("background");
                if (backgroundElement != null && !backgroundElement.isJsonNull()) {
                    backgroundValue = backgroundElement.getAsString();
                }

                JsonElement colorElement = currentIdObject.get("color");
                if (colorElement != null && !colorElement.isJsonNull()) {
                    colorValue = colorElement.getAsString();
                }

                JsonElement showClockElement = currentIdObject.get("showClock");
                if (showClockElement != null && !showClockElement.isJsonNull()) {
                    showClockValue = showClockElement.getAsBoolean();
                }

                JsonElement showWeatherElement = currentIdObject.get("showWeather");
                if (showWeatherElement != null && !showWeatherElement.isJsonNull()) {
                    showWeatherValue = showWeatherElement.getAsBoolean();
                }

                JsonElement hideRowElement = currentIdObject.get("hideRow");
                if (hideRowElement != null && !hideRowElement.isJsonNull()) {
                    hideRowValue = hideRowElement.getAsJsonArray();
                }
            }

            backgroundTextField = new TextFieldWidget(client.textRenderer, 20, this.height / 6 + 45, 200,
                    20, Text.of(""));
            this.addSelectableChild(backgroundTextField);
            backgroundTextField.setMaxLength(Integer.MAX_VALUE);
            backgroundTextField.setText(backgroundValue);

            colorTextField = new TextFieldWidget(client.textRenderer, 20, this.height / 6 + 70, 200,
                    20, Text.of(""));
            this.addSelectableChild(colorTextField);
            colorTextField.setMaxLength(Integer.MAX_VALUE);
            colorTextField.setText(colorValue);

            showWeatherCheckbox = new CheckboxWidget(20, this.height / 6 + 95, 200, 20,
                    Text.of("Show Weather"), showWeatherValue) {
                @Override
                public void onPress() {
                    super.onPress();
                    jsonObject.getAsJsonArray("pids_images").get(currentIdIndex).getAsJsonObject()
                            .addProperty("showWeather", isChecked());
                }
            };
            this.addDrawableChild(showWeatherCheckbox);

            showClockCheckbox = new CheckboxWidget(20, this.height / 6 + 120, 200, 20,
                    Text.of("Show Clock"), showClockValue) {
                @Override
                public void onPress() {
                    super.onPress();
                    jsonObject.getAsJsonArray("pids_images").get(currentIdIndex).getAsJsonObject().addProperty(
                            "showClock",
                            isChecked());
                }
            };
            this.addDrawableChild(showClockCheckbox);

            this.addDrawableChild(new ButtonWidget(10, this.height / 6 - 5, 20, 20, Text.of("<"), button -> {
                currentIdIndex = (currentIdIndex - 1 + jsonObject.getAsJsonArray("pids_images").size())
                        % jsonObject.getAsJsonArray("pids_images").size();
                updateFieldsBasedOnCurrentId();
            }));
            this.addDrawableChild(
                    new ButtonWidget(this.width - 30, this.height / 6 - 5, 20, 20, Text.of(">"), button -> {
                        currentIdIndex = (currentIdIndex + 1) % jsonObject.getAsJsonArray("pids_images").size();
                        updateFieldsBasedOnCurrentId();
                    }));

            int totalWidth = hideRowCheckboxes.length * 30;

            int horizontalGap = (this.width - totalWidth) / (hideRowCheckboxes.length + 1);

            int startX = horizontalGap;

            for (int i = 0; i < hideRowCheckboxes.length; i++) {
                int x = startX + i * (30 + horizontalGap) - 15;
                int y = this.height - 60;
                final int index = i;
                hideRowCheckboxes[i] = new CheckboxWidget(x, y, 20, 20, Text.of("Hide Row " + (i + 1)), false) {
                    @Override
                    public void onPress() {
                        super.onPress();
                        if (jsonObject != null && jsonObject.has("pids_images")) {
                            JsonArray pidsImages = jsonObject.getAsJsonArray("pids_images");
                            if (currentIdIndex >= 0 && currentIdIndex < pidsImages.size()) {
                                JsonObject currentIdObject = pidsImages.get(currentIdIndex).getAsJsonObject();
                                if (currentIdObject.has("hideRow")) {
                                    JsonArray hideRowArray = currentIdObject.getAsJsonArray("hideRow");
                                    if (index >= 0 && index < hideRowArray.size()) {
                                        hideRowArray.set(index, new JsonPrimitive(isChecked()));
                                    }
                                }
                            }
                        }
                    }
                };
                this.addDrawableChild(hideRowCheckboxes[i]);
            }

            updateFieldsBasedOnCurrentId();
            checkForModUpdates();
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        if (isComp == true) {
            initWidgets(matrices, mouseX, mouseY, delta);
        }
        super.render(matrices, mouseX, mouseY, delta);

        if (isComp == true) {
            String categoryName = categories.get(currentCategoryIndex);
            drawCenteredText(matrices, this.textRenderer, Text.of(categoryName), this.width / 2, 20, 0xFFFFFF);

            if (jsonObject != null && jsonObject.has("pids_images")) {
                JsonArray pidsImages = jsonObject.getAsJsonArray("pids_images");
                if (currentIdIndex >= 0 && currentIdIndex < pidsImages.size()) {
                    JsonObject currentIdObject = pidsImages.get(currentIdIndex).getAsJsonObject();
                    String idName = currentIdObject.get("id").getAsString();
                    drawCenteredText(matrices, this.textRenderer, Text.of("Selected ID: " + idName), this.width / 2,
                            this.height / 6, 0xFFFFFF);
                }
            }

            drawTextWithShadow(matrices, this.textRenderer, this.title, 10, this.height - 20, 0xFFFFFF);
            drawStringWithShadow(matrices, this.textRenderer, updateMessage,
                    this.width - this.textRenderer.getWidth(updateMessage) - 10, this.height - 20, 0xFFFFFF);

            JsonObject currentIdObject = null;
            if (jsonObject != null && jsonObject.has("pids_images")) {
                JsonArray pidsImages = jsonObject.getAsJsonArray("pids_images");
                if (currentIdIndex >= 0 && currentIdIndex < pidsImages.size()) {
                    currentIdObject = pidsImages.get(currentIdIndex).getAsJsonObject();
                }
            }

            renderBackgroundImage(matrices, currentIdObject);

            if (idTextField.isMouseOver(mouseX, mouseY)) {
                renderTooltip(matrices, Text.of("The name of the template"), mouseX, mouseY);
            } else if (backgroundTextField.isMouseOver(mouseX, mouseY)) {
                renderTooltip(matrices, Text.of("The background path of the PID"), mouseX, mouseY);
            } else if (colorTextField.isMouseOver(mouseX, mouseY)) {
                renderTooltip(matrices, Text.of("The color of the text shown on the PID"), mouseX, mouseY);
            }
        }

        if (isComp == false) {
            drawCenteredText(matrices, this.textRenderer, Text.of("No compatible resource packs are loaded"),
                    this.width / 2, this.height / 2 - 10, 0xFFFFFF);
            drawTextWithShadow(matrices, this.textRenderer, this.title, 10, this.height - 20, 0xFFFFFF);
            drawStringWithShadow(matrices, this.textRenderer, updateMessage,
                    this.width - this.textRenderer.getWidth(updateMessage) - 10, this.height - 20, 0xFFFFFF);
        }
    }

    private void initWidgets(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        idTextField.render(matrices, mouseX, mouseY, delta);
        backgroundTextField.render(matrices, mouseX, mouseY, delta);
        colorTextField.render(matrices, mouseX, mouseY, delta);
    }

    private void updateFieldsBasedOnCurrentId() {
        if (jsonObject != null && jsonObject.has("pids_images")) {
            JsonArray pidsImages = jsonObject.getAsJsonArray("pids_images");
            if (currentIdIndex >= 0 && currentIdIndex < pidsImages.size()) {
                JsonObject currentIdObject = pidsImages.get(currentIdIndex).getAsJsonObject();
                idTextField.setText(getStringOrNull(currentIdObject, "id"));
                backgroundTextField.setText(getStringOrNull(currentIdObject, "background"));
                colorTextField.setText(getStringOrNull(currentIdObject, "color"));
                setCheckboxState(showWeatherCheckbox, getBooleanOrNull(currentIdObject, "showWeather"));
                setCheckboxState(showClockCheckbox, getBooleanOrNull(currentIdObject, "showClock"));
                if (currentIdObject.has("hideRow")) {
                    JsonArray hideRowArray = currentIdObject.getAsJsonArray("hideRow");
                    for (int i = 0; i < hideRowCheckboxes.length; i++) {
                        Boolean hideRow = null;
                        if (i < hideRowArray.size()) {
                            hideRow = hideRowArray.get(i).getAsBoolean();
                        }
                        setCheckboxState(hideRowCheckboxes[i], hideRow != null ? hideRow : false);
                    }
                } else {
                    for (CheckboxWidget checkbox : hideRowCheckboxes) {
                        setCheckboxState(checkbox, false);
                    }
                }
            }
        }
    }

    private String getStringOrNull(JsonObject jsonObject, String key) {
        if (jsonObject.has(key) && !jsonObject.get(key).isJsonNull()) {
            return jsonObject.get(key).getAsString();
        }
        return "";
    }

    private Boolean getBooleanOrNull(JsonObject jsonObject, String key) {
        if (jsonObject.has(key) && !jsonObject.get(key).isJsonNull()) {
            return jsonObject.get(key).getAsBoolean();
        }
        return false;
    }

    private void setCheckboxState(CheckboxWidget checkbox, boolean newState) {
        if (checkbox.isChecked() != newState) {
            checkbox.onPress();
        }
    }

    private void checkForModUpdates() {
        try {
            String apiUrl = "https://api.modrinth.com/v2/project/visual-pids-editor/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.19.2%22%5D";

            String response = makeHttpRequest(apiUrl);

            JsonArray versions = JsonParser.parseString(response).getAsJsonArray();

            for (JsonElement version : versions) {
                String versionNumber = version.getAsJsonObject().get("version_number").getAsString();

                if (compareVersions(versionNumber, modVersion) > 0) {
                    updateMessage = "Update available!";
                }
            }
        } catch (IOException e) {
            updateMessage = "Version " + modVersion;
            e.printStackTrace();
        }
    }

    private String makeHttpRequest(String apiUrl) throws IOException {
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("GET");

            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                return response.toString();
            } else {
                throw new IOException("HTTP request failed with response code: " + responseCode);
            }
        } finally {
            connection.disconnect();
        }
    }

    private int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int part1 = (i < parts1.length) ? Integer.parseInt(parts1[i]) : 0;
            int part2 = (i < parts2.length) ? Integer.parseInt(parts2[i]) : 0;

            if (part1 < part2) {
                return -1;
            } else if (part1 > part2) {
                return 1;
            }
        }

        return 0;
    }
}
