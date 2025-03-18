
package programmingtheiot.gda.system;

import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import programmingtheiot.common.ConfigConst;
import java.io.IOException;
import java.util.logging.Logger;

public class SystemDiskUtilTask extends BaseSystemUtilTask {

    // private
    private static final Logger _Logger = Logger.getLogger(BaseSystemUtilTask.class.getName());

    private final Path rootPath;

    // constructors

    public SystemDiskUtilTask()
    {
        super(ConfigConst.NOT_SET, ConfigConst.DEFAULT_TYPE_ID);
        this.rootPath = Paths.get("/");
    }


    // public methods

    @Override
    public float getTelemetryValue() {
        try {
            FileStore fileStore = Files.getFileStore(rootPath);
            long totalSpace = fileStore.getTotalSpace();
            long usableSpace = fileStore.getUsableSpace();

            if (totalSpace <= 0) {
                _Logger.warning("Total space is zero or negative, can't calculate disk usage.");
                return -1.0f;
            }

            long usedSpace = totalSpace - usableSpace;
            float usagePercent = (float) usedSpace / totalSpace * 100.0f;

            return usagePercent;

        } catch (IOException e) {
            _Logger.severe("Error retrieving disk usage: " + e.getMessage());
            return -1.0f;
        }
    }

}


