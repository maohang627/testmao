package com.saysth.commons.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Properties文件工具类
 * 
 * @author
 */
public abstract class PropertiesUtils {
	// private static Logger logger = LoggerFactory.getLogger(PropertiesUtils.class);
	private static final String DEFAULT_ENCODING = "UTF-8";

	// private static PropertiesPersister propertiesPersister = new DefaultPropertiesPersister();
	// private static ResourceLoader resourceLoader = new DefaultResourceLoader();

	/**
	 * 载入多个properties文件, 相同的属性在最后载入的文件中的值将会覆盖之前的载入 文件路径使用Spring Resource格式，文件编码使用UTF-8
	 * 
	 * @see org.springframework.beans.factory.config.PropertyPlaceholderConfigurer
	 */
	public static Properties loadProperties(String... resourcesPaths) throws IOException {
		Properties props = new Properties();

		for (String location : resourcesPaths) {
			System.out.println("Loading property file from:" + location);

			InputStream is = null;
			try {
				is = PropertiesUtils.class.getResourceAsStream("location");
				props.load(is);
			} catch (IOException ex) {
				System.out.println("Could not load properties from classpath:" + location + ": " + ex.getMessage());
			} finally {
				if (is != null) {
					is.close();
				}
			}
		}
		return props;
	}

}
