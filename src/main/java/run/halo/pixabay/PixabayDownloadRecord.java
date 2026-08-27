package run.halo.pixabay;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * Global dedupe record: stores every downloaded Pixabay image id, so the same
 * image is never uploaded twice (same rule as the Python version).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "plugin.halo.run", version = "v1alpha1", kind = "PixabayDownloadRecord",
    plural = "pixabaydownloadrecords", singular = "pixabaydownloadrecord")
public class PixabayDownloadRecord extends AbstractExtension {

    public static final String RECORD_NAME = "pixabay-download-record";

    private Spec spec = new Spec();

    @Data
    public static class Spec {

        private Set<String> downloadedIds = new LinkedHashSet<>();

        private String lastRunAt;

        private String lastRunMessage;

        private Integer lastRunAdded;

        private Integer lastRunTotal;
    }
}
