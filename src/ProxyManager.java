/*
 *  Author:      Dat Q Nguyen
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class ProxyManager {
	
	private ArrayList <Proxy> mProxyList;
	private static ProxyManager mProxyManager;
	private String mProxyListURL = "http://proxylist.hidemyass.com";
	private InetAddress mPublicIP;
	private String mCheckIPURL = "http://checkip.dyndns.org";
	private boolean mVerified = false;
	private int mLastPage = 1;
	private int mReadTimeout = 10;
	private int mConnectTimeout = 100;
	private int mNoTestThread = 4;
	private ExecutorService mPool;

	private ProxyManager () throws Exception {
	}
	
	public static ProxyManager getInstance () throws Exception {
		if (mProxyManager == null) {
			mProxyManager = new ProxyManager();
		}
		return mProxyManager;
	}
	
	public boolean isVerified() {
		return mVerified;
	}

	public InetAddress getPublicIP (Document pJoupDocument) throws IOException {
		if (mPublicIP == null) {
			System.out.println("Determining public ip...");
			Pattern p = Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})", Pattern.MULTILINE | Pattern.DOTALL);
			Matcher m = p.matcher(pJoupDocument.html());
			while (m.find()) {
				mPublicIP = InetAddress.getByName(m.group(1));
			}
		}
		return mPublicIP;
	}
	
	private boolean verifyProxy (Proxy pProxy) {
		//System.out.println("Testing Proxy: " + pProxy.address().toString());
		try {
			Document doc = getDocument (mCheckIPURL, pProxy);
			System.out.println("accepted: " + pProxy.address().toString());
			doc = null;
			return true;
		} catch (IOException e) {
			//System.out.println("omitted " + pProxy.address().toString() + " reason:" + e.getMessage());
			return false;
		}
	}
	
	public int getProxyCount () {
		if (mProxyList != null) {
			return mProxyList.size();
		} else {
			return 0;
		}
	}
	
	public Document getDocument (String pURL, Proxy pProxy) throws MalformedURLException, IOException {
		URL url = new URL(pURL);
		HttpURLConnection uc = (HttpURLConnection) url.openConnection(pProxy);
		if (mConnectTimeout > 0) uc.setConnectTimeout(mConnectTimeout);
		if (mReadTimeout > 0) uc.setReadTimeout(mReadTimeout);
		uc.connect();
		String line = null;
		StringBuffer tmp = new StringBuffer();
		BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
	    while ((line = in.readLine()) != null) {
	      tmp.append(line);
	    }
	    uc.disconnect();
	    in.close();
	    uc = null;
	    in = null;
		return Jsoup.parse(String.valueOf(tmp));
	}
		
	public synchronized ArrayList <Proxy> getProxyList () throws Exception {
		if (mProxyList!=null) {
			return mProxyList;			
		} else {
			loadProxy();
			return mProxyList;
		}
	}
	
	public synchronized void checkProxyList () {
		if (mPool==null) mPool = Executors.newFixedThreadPool(mNoTestThread);
		
		final ArrayList <Proxy> toRemove = new ArrayList<Proxy>(); 
		if (mProxyList != null) {
			for (final Proxy p : mProxyList) {
				mPool.execute(
					new Runnable() {
						public void run() {									
							if (!mProxyManager.verifyProxy(p)) {
								toRemove.add(p);
							}
						}
					}
				);
			}
		}
		
		// make sure that the mPool is shutdown at the end and we wait for the 
		mPool.shutdown();
		while (!mPool.isTerminated()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		// okay it is shutdown, now we need to remove the proxy from the list.
		if (toRemove.size() > 0) {
			System.out.println("Having issues with " + toRemove.size() + " from the proxy list");
			// we need to remove the proxy 
			for (Proxy p : toRemove) {
				mProxyList.remove(p);
			}
			toRemove.clear();
		}

		mPool = null;
	}
	
	public synchronized void cleanProxyList () {
		if (mPool==null) mPool = Executors.newFixedThreadPool(mNoTestThread);
		
		final ArrayList <Proxy> toRemove = new ArrayList<Proxy>(); 
		if (mProxyList != null) {
			for (final Proxy p : mProxyList) {
				mPool.execute(
					new Runnable() {
						public void run() {									
							if (!mProxyManager.verifyProxy(p)) {
								toRemove.add(p);
							}
						}
					}
				);
			}
		}
		
		// make sure that the mPool is shutdown at the end and we wait for the 
		mPool.shutdown();
		while (!mPool.isTerminated()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		
		
		if (toRemove.size() > 0) {
			System.out.println("Removing " + toRemove.size() + " from the proxy list");
			for (Proxy p : toRemove) {
				mProxyList.remove(p);
			}
			toRemove.clear();
		}

		mPool = null;
	}
	
	private synchronized void clearProxyList () {
		synchronized (mProxyList) {
			if (mProxyList != null) mProxyList.clear();
		}
	}
		
	private synchronized void loadProxy () throws IOException {
		Connection jsc = Jsoup.connect (mProxyListURL).timeout(5000);
		Document hmasite = jsc.get();
		
		//get the last page
		for (Element e : hmasite.select(".pagination.ng-scope")) {
			Pattern p = Pattern.compile("([0-9]+)$", Pattern.MULTILINE);
			Matcher m;
			ArrayList <Integer> pagenum = new ArrayList <Integer> ();
			
			for (Element at : e.select ("a[href]")) {
				m = p.matcher(at.text());
				if (m.find()) {
					//System.out.println(m.group(1));
					pagenum.add(Integer.parseInt(m.group(1)));
				}
			}
			
			mLastPage = pagenum.get(pagenum.size()-1);
			
			pagenum = null;
			p = null;
			m = null;
		}

		jsc = null;
		hmasite = null;

		mProxyList = new ArrayList<Proxy>();
		for (int lp = 1; lp <= mLastPage; lp++) {
			// create a connection to each page
			jsc = Jsoup.connect (mProxyListURL + "/" + Integer.toString(lp) + "#listable").timeout(5000);
			hmasite = jsc.get();
			
			for (Element proxyTable : hmasite.select (".hma-table")) {
				int i = 0;
				String ip = "";
				String port = "";
				String type = "";
				
				for (Element td : proxyTable.select("td")) {
					// Second column of the proxy list table contains the IP of the proxy
					if (i == 1) {
						ArrayList <HMAStylePair> stylepairs = new ArrayList <HMAStylePair> ();
						
						String code = td.html();
						
						Pattern p = Pattern.compile("\\.(\\w+)\\{\\w+:(\\w+)}", Pattern.MULTILINE | Pattern.DOTALL);
						Matcher m = p.matcher(code);
						while (m.find()) {
							stylepairs.add(new HMAStylePair(m.group(1), m.group (2)));
						}
						
						//remove all /r /n
						code = code.replaceAll("[\\r\\n]", "");
						code = code.replaceAll("<style>.*?</style>", "");
						
						// clear the style that has none for display
						for (HMAStylePair sp : stylepairs) {
							if (sp.isDisplayFlag() == false) {
								code = code.replaceAll ("<span class=\"" + sp.getKey() +"\">.*?</span>", "");
								code = code.replaceAll ("<div class=\"" + sp.getKey() +"\">.*?</span>", "");
							}
						}
						
						//remove all span and div tags that contain a class that is not in the inline CSS and it's not a number
						// or it has - in the class name.
						p = Pattern.compile("class=\"(.*?)\"", Pattern.MULTILINE | Pattern.DOTALL);
						m = p.matcher(code);
						while (m.find()) {
							boolean found = false;
							for (HMAStylePair sp : stylepairs) {
								if (sp.getKey().equalsIgnoreCase(m.group(1))) {
									found = true;
								}
							}
							
							if (found == false && (!m.group(1).matches("^\\d+$") || m.group(1).matches("\\-"))) {
								code = code.replaceAll ("<div class=\"" + m.group(1) +"\">.*?</span>", "");
								code = code.replaceAll ("<span class=\"" + m.group(1) +"\">.*?</span>", "");
							}
						}
											
						// clear all span tags with attribute value display:none
						code = code.replaceAll("<span style=\"display:none\">.*?</span>", "");
						
						// clear all div tags with attribute value display:none
						code = code.replaceAll("<div style=\"display:none\">.*?</div>", "");
						
						//replace all span tags without any values
						code = code.replaceAll("<span></span>", "");
											
						// remove space characters 
						code = code.replaceAll("\\s", "");
						
						//Extract the text only
						ip = Jsoup.parse(code).text();	
					}
					
					if (i==2) {
						// Third column of the proxy list table contains the Port of the proxy
						port = td.text();
					} 	
					
					if (i==6) {
						type = td.text();
					}
					
					// reset the column count including ips and all the other variables
					if (i > 6) {				
						// make sure the ip is in correct format and then add proxy to the list
						if (ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}") && port.matches("^[0-9]+$")){
							//System.out.println("Adding " + type + " proxy: " + ip + ":" + port + " to the proxy list...");
							Proxy p = null;
							if (type.matches("HTTP[S]*"))  {
								p = new Proxy (Proxy.Type.HTTP, new InetSocketAddress(ip, Integer.parseInt(port)));
							} else if (type.matches("socks4/5")) {
								p = new Proxy (Proxy.Type.SOCKS, new InetSocketAddress(ip, Integer.parseInt(port)));
							}
							mProxyList.add(p);
						} else {
							//System.out.println("Excluding proxy: " + ip + ":" + port + " from the proxy list...");
						}

						i = 0;
						ip = "";
						port = "";
						type = "";
					} else {
						i++;
					}
				}
			}
			
			jsc = null;
			hmasite = null;
		}	
	}
	
	public Proxy getProxy () {
		if (mProxyList!=null) {
			boolean foundGoodProxy=false;
			Random random = new Random();
			Proxy p = null;
			while (foundGoodProxy) {
				p = mProxyList.get(random.nextInt(mProxyList.size()));
				if (verifyProxy(p)) {
					foundGoodProxy = true;
				}
			}
			random = null;
			return p;
		} else {
			return null;
		}
	}
	
	public int getReadTimeout() {
		return mReadTimeout;
	}

	public int getConnectTimeout() {
		return mConnectTimeout;
	}

	public void setReadTimeout(int pReadTimeout) {
		mReadTimeout = pReadTimeout;
	}

	public void setConnectTimeout(int pConnectTimeout) {
		mConnectTimeout = pConnectTimeout;
	}

	public int getTestThreadTotal() {
		return mNoTestThread;
	}

	public void setTestThreadNumber(int pNoTestThread) {
		mNoTestThread = pNoTestThread;
	}

	public static void main(String[] args) {
		try {
			// Create instance
			ProxyManager pm = ProxyManager.getInstance();
			
			//load the proxies
			long starttime = System.nanoTime();
			System.out.println("Extract Proxies from HMA and put into a list...");
			ArrayList <Proxy> proxylist = pm.getProxyList();
			long endtime = System.nanoTime();
			long duration = (long) ((endtime - starttime)/1000000000);
			System.out.println("Loaded proxies in " + duration + " seconds");
			System.out.println("Total proxies in list now: " + pm.getProxyCount());
			
				
			//clean the proxy list
			System.out.println("cleaning the proxy list...");
			// set the default connection time and read time to filter out the slow proxies
			pm.setConnectTimeout(2000); //500 msec max to connect
			pm.setReadTimeout(10000); //3 sec max to read the page
			pm.setTestThreadNumber(4); // set 10 threads
			
			starttime = System.nanoTime();
			pm.checkProxyList();
			endtime = System.nanoTime();
			
			duration = (long) ((endtime - starttime)/1000000000);
			System.out.println("Purged proxies in " + duration + " seconds");
			System.out.println("Total Proxies in list: " + pm.getProxyCount());
			
			pm.clearProxyList();			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
