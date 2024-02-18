package com.doojis.jp.mixin;

import com.doojis.jp.gui.VisualEditorScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(OptionsScreen.class)
public abstract class OptionsMixin extends Screen {

    protected OptionsMixin(Text title) {
        super(title);
    }

    @Inject(at = @At("TAIL"), method = "init")
    private void addButton(CallbackInfo ci) {
        if (checkModFileExists("rpdl-fabric-")) {
            ButtonWidget newButton = new ButtonWidget(this.width / 2 + 5, this.height / 6 + 144 - 6, 150, 20,
                    Text.translatable("options.jpopt"), button -> {
                        VisualEditorScreen ve = new VisualEditorScreen(client);
                        ve.init(this.client, this.width, this.height);
                        this.client.setScreen(ve);
                    });
            this.addDrawableChild(newButton);
        } else {
            ButtonWidget newButton = new ButtonWidget(this.width / 2 - 155, this.height / 6 + 144 - 6, 150, 20,
                    Text.translatable("options.jpopt"), button -> {
                        VisualEditorScreen ve = new VisualEditorScreen(client);
                        ve.init(this.client, this.width, this.height);
                        this.client.setScreen(ve);
                    });
            this.addDrawableChild(newButton);
        }
    }

    private boolean checkModFileExists(String modFileName) {
        String minecraftDir = System.getProperty("user.dir");
        String modsFolderPath = minecraftDir + File.separator + "mods";
        File modsFolder = new File(modsFolderPath);
        if (modsFolder.exists() && modsFolder.isDirectory()) {
            File[] modFiles = modsFolder.listFiles();
            if (modFiles != null) {
                for (File modFile : modFiles) {
                    if (modFile.getName().startsWith(modFileName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}