package cn.hjdai.ztimer.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class IpUtil {

	/**
	 * 获取本机IP
	 * 
	 * @return 本地IP
	 */
	public static String getLocalIP() {
		try {
			if (isWindowsOS()) {
				return InetAddress.getLocalHost().getHostAddress();
			} else {
				return getLinuxLocalIp();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 获取本地Host名称
	 * 
	 * @return 本地 hostname
	 * @throws UnknownHostException
	 */
	public static String getLocalHostName() throws UnknownHostException {
		return InetAddress.getLocalHost().getHostName();
	}

	private static String getLinuxLocalIp() throws SocketException {
		String ip = "";
		for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
			NetworkInterface intf = en.nextElement();
			String name = intf.getName();
			if (!name.contains("docker") && !name.contains("lo")) {
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						String ipaddress = inetAddress.getHostAddress().toString();
						if (!ipaddress.contains("::") && !ipaddress.contains("0:0:") && !ipaddress.contains("fe80")) {
							ip = ipaddress;
						}
					}
				}
			}
		}
		return ip;
	}

	private static boolean isWindowsOS() {
		boolean isWindowsOS = false;
		String osName = System.getProperty("os.name");
		if (osName.toLowerCase().indexOf("windows") > -1) {
			isWindowsOS = true;
		}
		return isWindowsOS;
	}

}
