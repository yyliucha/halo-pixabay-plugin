package com.yyliucha.pixabay;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * Main plugin class managing the plugin lifecycle.
 *
 * <p>Custom extension models must be registered into the {@link SchemeManager}
 * manually when the plugin starts (same pattern as official plugins, e.g.
 * plugin-links); otherwise the extension client fails with
 * "Scheme not found for ..." when the model is first used.
 *
 * @author yyliucha
 * @since 1.0.0
 */
@Component
@EnableScheduling
public class PixabayPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public PixabayPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        // Register the download record custom model so it can be fetched,
        // created and updated through the extension client.
        schemeManager.register(PixabayDownloadRecord.class);
    }

    @Override
    public void stop() {
        schemeManager.unregister(Scheme.buildFromType(PixabayDownloadRecord.class));
    }
}
