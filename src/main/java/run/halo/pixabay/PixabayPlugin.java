package run.halo.pixabay;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * Main plugin class managing the plugin lifecycle.
 *
 * @author yyliucha
 * @since 1.0.0
 */
@Component
public class PixabayPlugin extends BasePlugin {

    public PixabayPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        // Beans (client, service, scheduler, endpoint) are auto-registered by Spring.
    }

    @Override
    public void stop() {
        // Nothing to clean up.
    }
}
