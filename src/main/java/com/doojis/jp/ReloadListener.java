package com.doojis.jp;

import com.doojis.jp.gui.VisualEditorScreen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import java.util.Collection;
import java.util.function.Supplier;

public class ReloadListener extends ResourcePackManager {
    private final MinecraftClient client;

    public ReloadListener(MinecraftClient client, ResourcePackProfile.Factory profileFactory,
            ResourcePackProvider... providers) {
        super(profileFactory, providers);
        this.client = client;
    }

    public ReloadListener(MinecraftClient client, ResourceType type, ResourcePackProvider... providers) {
        this(client,
                (name, displayName, alwaysEnabled, packFactory, metadata, direction, source) -> new ResourcePackProfile(
                        name, displayName, alwaysEnabled, packFactory, metadata, type, direction, source),
                providers);
    }

    @Override
    public void scanPacks() {
        super.scanPacks();

        ((VisualEditorScreen) client.currentScreen).RL();
    }
}
