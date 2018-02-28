package yml;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import com.google.common.base.Strings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class YmlReader {
    public static final Logger logger = LoggerFactory.getLogger(YmlReader.class);

    public YmlReader() {
    }

    public static <T> T read(InputStream inputStream, Class<T> clazz) {
        Yaml yaml = new Yaml(new Constructor(clazz));
        return (T)yaml.load(inputStream);
    }

    public static <T> T readFromClasspath(String path, Class<T> clazz) {
        logger.info("Reading the configuration file from class path {}", path);
        InputStream in = YmlReader.class.getResourceAsStream(path);
        if(in != null) {
            return read(in, clazz);
        } else {
            throw new YmlReader.InvalidYmlPathException("The file " + path + " doesn\'t exit in the classpath");
        }
    }

    public static <T> T readFromFile(String path, Class<T> clazz) {
        if(!Strings.isNullOrEmpty(path)) {
            File file = new File(path);
            return readFromFile(file, clazz);
        } else {
            throw new YmlReader.InvalidYmlPathException("The Yml file argument is not valid: " + path);
        }
    }

    public static <T> T readFromFile(File file, Class<T> clazz) {
        if(file != null) {
            try {
                return read(new FileInputStream(file), clazz);
            } catch (FileNotFoundException var3) {
                throw new YmlReader.InvalidYmlPathException("The file " + file.getAbsolutePath() + " doesn\'t exit in the file system");
            }
        } else {
            throw new YmlReader.InvalidYmlPathException("The Yml file argument is null");
        }
    }

    public static Map<String, ?> readFromFile(File file) {
        Yaml yaml = new Yaml();

        try {
            return (Map)yaml.load(new FileReader(file));
        } catch (FileNotFoundException var3) {
            throw new YmlReader.InvalidYmlPathException("The file " + file.getAbsolutePath() + " doesn\'t exit in the file system");
        }
    }

    public static Map<String, ?> readFromFileAsMap(File file) {
        Yaml yaml = new Yaml();

        try {
            return (Map)yaml.load(new FileReader(file));
        } catch (FileNotFoundException var3) {
            throw new YmlReader.InvalidYmlPathException("The file " + file.getAbsolutePath() + " doesn\'t exit in the file system");
        }
    }

    public static class InvalidYmlPathException extends RuntimeException {
        public InvalidYmlPathException(String s) {
            super(s);
        }

        public InvalidYmlPathException(String s, Throwable throwable) {
            super(s, throwable);
        }
    }
}
