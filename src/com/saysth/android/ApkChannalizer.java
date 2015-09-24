package com.saysth.android;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * 对APK包进行分渠道打包
 * 
 * @author KelvinZ
 * @see(saysth.com)
 * 
 */
public class ApkChannalizer {
	public static List<ChannelInfo> channelList = new LinkedList<ChannelInfo>();
	// 这个对应classpath的根目录下的文件，如：classes/AndroidConfig.properties
	//sss
	private static final String config = "AndroidConfig";

	/** APK解压工具 */
	public static String apkToolExe;
	/** APK名称 */
	public static String appName;
	/** 版本号 */
	public static String appVersion;
	/** 渠道占位符 */
	public static String channelIdHolder;
	/** 带渠道占位符的APK源文件 */
	public static String originalApk;
	/** 生成分渠道APK的目录 */
	public static String outputFolder;
	/** 临时文件目录 */
	public static String tmpFileFolder;

	/** keystore相关 */
	public static String keystorePath;
	public static String storePwd;
	public static String keyPwd;
	public static String alias;

	/**
	 * 执行该方法开始处理APK
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		init();
		loadChannelInfo(); // 加载渠道信息
		extractApk(); // 解压APK
		replaceChannel(); // 替换渠道信息
	}

	public static void init() {
		ResourceBundle rb = ResourceBundle.getBundle(config, Locale.getDefault());
		apkToolExe = rb.getString("apkToolExe");
		appName = rb.getString("appName");
		appVersion = rb.getString("appVersion");
		channelIdHolder = rb.getString("channelIdHolder");
		originalApk = rb.getString("originalApk");
		outputFolder = rb.getString("outputFolder");
		tmpFileFolder = rb.getString("tmpFileFolder");

		keystorePath = rb.getString("keystorePath");
		storePwd = rb.getString("storePwd");
		keyPwd = rb.getString("keyPwd");
		alias = rb.getString("alias");
	}

	/**
	 * 载入渠道信息
	 */
	public static void loadChannelInfo() {
		ResourceBundle rb = ResourceBundle.getBundle("AndroidChannels", Locale.getDefault());
		Set<String> keys = rb.keySet();
		for (String key : keys) {
			String channelName = "";
			try {
				channelName = new String(rb.getString(key).getBytes("ISO-8859-1"), "UTF-8");
			} catch (Exception e) {
				e.printStackTrace();
			}
			channelList.add(new ChannelInfo(key, channelName));
		}
	}

	/**
	 * 解压APK
	 */
	public static void extractApk() {
		createFolder(outputFolder);
		createFolder(tmpFileFolder);
		String cmd = String.format(apkToolExe + " d -f %s -o %s%s", originalApk, tmpFileFolder, appName);
		//String cmd = String.format(apkToolExe + " d -f %s ", originalApk);
		System.out.println(cmd);
		execShellCmd(cmd);
	}

	/**
	 * 替换渠道变量
	 */
	public static void replaceChannel() {
		try {
			String outPath = String.format("%s%s/AndroidManifest.xml", tmpFileFolder, appName);
			String content = read(outPath);
			for (int i = 0; i < channelList.size(); i++) {
				String tmpContent = content;
				ChannelInfo channel = channelList.get(i);
				String channelId = channel.channelId;
				tmpContent = tmpContent.replaceFirst(channelIdHolder, channelId);
				write(tmpContent, outPath);
				generatePackage(channel);
			}
			write(content, outPath);
			clearTmpFiles();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 根据渠道信息生成APK
	 * 
	 * @param channel
	 */
	private static void generatePackage(ChannelInfo channel) {
		try {
			String channelName = channel.channelName;
			System.out.println("\n\n======Handling: " + channel.toString() + "======");
			System.out.println("Channel: " + channelName + " gen unsigned APK start...");

			// 生成未签名APK包
			String unsignedFilename = String.format("%s%s_unsigned.apk", tmpFileFolder, appName);
			//String pack_cmd = String.format(apkToolExe + " b %s%s %s", tmpFileFolder, appName, unsignedFilename);
			
			String pack_cmd = String.format(apkToolExe + " b -o %s %s%s", unsignedFilename,tmpFileFolder, appName );
			System.out.println("start pack " + pack_cmd);
			execShellCmd(pack_cmd);

			// 对未签名包进行签名处理
			String signFilename = String.format("%s%s_v%s_%s.apk", outputFolder, appName, appVersion, channel.channelId);
			String sign_cmd = String.format("jarsigner -keystore %s -storepass %s -keypass %s -signedjar %s %s %s",
					keystorePath, storePwd, keyPwd, signFilename, unsignedFilename, alias);
			System.out.println("start sign " + sign_cmd);
			execShellCmd(sign_cmd);
			System.out.println("Channel: " + channelName + " finished.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** 根据文件全路径名读取文件内容至字符串 */
	private static String read(String filename) {
		FileInputStream fis = null;
		String retVal = null;
		try {
			File file = new File(filename);
			if (file != null && file.exists()) {
				fis = new FileInputStream(file);
				retVal = readInStream(fis);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			closeIOStream(fis, null);
		}
		return retVal;
	}

	/** 把文件输入流读到字符串里 */
	private static String readInStream(FileInputStream inStream) {
		String retVal = null;
		try {
			ByteArrayOutputStream outStream = new ByteArrayOutputStream();
			byte[] buffer = new byte[512];
			int length = -1;
			while ((length = inStream.read(buffer)) != -1) {
				outStream.write(buffer, 0, length);
			}
			retVal = outStream.toString();
			closeIOStream(inStream, outStream);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return retVal;
	}

	/**
	 * 方便的关闭<code>OutputStream</code>
	 * 
	 * @param os
	 */
	public static void closeIOStream(InputStream is, OutputStream os) {
		if (is != null) try {
			is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (os != null) try {
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 写文件内容
	 * 
	 * @param content
	 * @param filename
	 */
	private static void write(String content, String filename) {
		if (content == null) content = "";

		File file = new File(filename);
		try {
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(content.getBytes());
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 创建目录至最深
	 * 
	 * @param[in] sPath 目录
	 * @return 是否创建成功
	 */
	private static boolean createFolder(final String sPath) {
		try {
			final File oPath = new File(sPath);
			if (!oPath.exists()) {
				oPath.mkdirs();
			}
			return true;
		} catch (final Exception e) {
			return false;
		}
	}

	private static void clearTmpFiles() {
		File file = new File(tmpFileFolder);
		file.deleteOnExit();
		// execShellCmd("rmdir /Q /S " + tmpFileFolder);
	}

	/**
	 * 执行命令行语句
	 * 
	 * @param cmd
	 */
	private static void execShellCmd(String cmd) {
		try {
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}

/**
 * 渠道信息由ID和Name组成
 */
class ChannelInfo {
	public String channelId;
	public String channelName;

	ChannelInfo(String channelId, String channelName) {
		this.channelId = channelId;
		this.channelName = channelName;
	}

	@Override
	public String toString() {
		return "[ID: " + channelId + " Name: " + channelName + "]";
	}

}
