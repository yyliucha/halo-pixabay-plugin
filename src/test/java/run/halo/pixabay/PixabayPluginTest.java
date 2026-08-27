package run.halo.pixabay;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.PluginContext;

class PixabayPluginTest {

    @Test
    void startShouldRegisterDownloadRecordScheme() {
        var schemeManager = mock(SchemeManager.class);
        var plugin = new PixabayPlugin(mock(PluginContext.class), schemeManager);

        plugin.start();

        verify(schemeManager).register(PixabayDownloadRecord.class);
    }

    @Test
    void stopShouldUnregisterDownloadRecordScheme() {
        var schemeManager = mock(SchemeManager.class);
        var plugin = new PixabayPlugin(mock(PluginContext.class), schemeManager);

        plugin.stop();

        verify(schemeManager).unregister(Scheme.buildFromType(PixabayDownloadRecord.class));
    }
}
