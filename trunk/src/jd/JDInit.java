//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd;

import java.awt.Toolkit;
import java.io.File;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import jd.config.Configuration;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.controlling.ByteBufferController;
import jd.controlling.DownloadController;
import jd.controlling.JDController;
import jd.controlling.JDLogger;
import jd.controlling.PasswordListController;
import jd.controlling.interaction.Interaction;
import jd.event.ControlEvent;
import jd.gui.UserIF;
import jd.gui.UserIO;
import jd.gui.swing.SwingGui;
import jd.gui.swing.components.linkbutton.JLink;
import jd.gui.swing.jdgui.GUIUtils;
import jd.gui.swing.jdgui.JDGui;
import jd.gui.swing.jdgui.JDGuiConstants;
import jd.gui.swing.jdgui.events.EDTEventQueue;
import jd.gui.swing.laf.LookAndFeelController;
import jd.http.Browser;
import jd.http.Encoding;
import jd.http.JDProxy;
import jd.nutils.ClassFinder;
import jd.nutils.JDFlags;
import jd.nutils.OSDetector;
import jd.nutils.io.JDIO;
import jd.parser.Regex;
import jd.plugins.HostPlugin;
import jd.plugins.OptionalPlugin;
import jd.plugins.PluginOptional;
import jd.utils.JDTheme;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

/**
 * @author JD-Team
 */

public class JDInit {

    private static final boolean TEST_INSTALLER = false;

    private static Logger logger = jd.controlling.JDLogger.getLogger();

    private static ClassLoader CL;

    private boolean installerVisible = false;

    public JDInit() {
    }

    public void checkUpdate() {
        if (JDUtilities.getResourceFile("webcheck.tmp").exists() && JDIO.getLocalFile(JDUtilities.getResourceFile("webcheck.tmp")).indexOf("(Revision" + JDUtilities.getRevision() + ")") > 0) {
            UserIO.getInstance().requestTextAreaDialog("Error", "Failed Update detected!", "It seems that the previous webupdate failed.\r\nPlease ensure that your java-version is equal- or above 1.5.\r\nMore infos at http://www.syncom.org/projects/jdownloader/wiki/FAQ.\r\n\r\nErrorcode: \r\n" + JDIO.getLocalFile(JDUtilities.getResourceFile("webcheck.tmp")));
            JDUtilities.getResourceFile("webcheck.tmp").delete();
            JDUtilities.getConfiguration().setProperty(Configuration.PARAM_WEBUPDATE_AUTO_RESTART, false);
        } else {

            Interaction.handleInteraction(Interaction.INTERACTION_APPSTART, false);
        }
        if (JDUtilities.getRunType() == JDUtilities.RUNTYPE_LOCAL_JARED) {
            String old = JDUtilities.getConfiguration().getStringProperty(Configuration.PARAM_UPDATE_VERSION, "");
            if (!old.equals(JDUtilities.getRevision())) {
                logger.info("Detected that JD just got updated");
                JDUtilities.getController().fireControlEvent(new ControlEvent(this, SplashScreen.SPLASH_FINISH));
                int status = UserIO.getInstance().requestHelpDialog(UserIO.NO_CANCEL_OPTION, JDL.LF("system.update.message.title", "Updated to version %s", JDUtilities.getRevision()), JDL.L("system.update.message", "Update successfull"), JDL.L("system.update.showchangelogv2", "What's new?"), "http://jdownloader.org/changes/index");
                if (JDFlags.hasAllFlags(status, UserIO.RETURN_OK) && JDUtilities.getConfiguration().getBooleanProperty(Configuration.PARAM_WEBUPDATE_AUTO_SHOW_CHANGELOG, true)) {
                    try {
                        JLink.openURL("http://jdownloader.org/changes/index");
                    } catch (Exception e) {
                        JDLogger.exception(e);
                    }
                }

            }
        }
        submitVersion();
    }

    private void submitVersion() {
        new Thread(new Runnable() {
            public void run() {
                if (JDUtilities.getRunType() == JDUtilities.RUNTYPE_LOCAL_JARED) {
                    String os = "unk";
                    if (OSDetector.isLinux()) {
                        os = "lin";
                    } else if (OSDetector.isMac()) {
                        os = "mac";
                    } else if (OSDetector.isWindows()) {
                        os = "win";
                    }
                    String tz = System.getProperty("user.timezone");
                    if (tz == null) tz = "unknown";
                    Browser br = new Browser();
                    br.setConnectTimeout(15000);
                    if (!JDUtilities.getConfiguration().getStringProperty(Configuration.PARAM_UPDATE_VERSION, "").equals(JDUtilities.getRevision())) {
                        try {
                            String prev = JDUtilities.getConfiguration().getStringProperty(Configuration.PARAM_UPDATE_VERSION, "");
                            if (prev == null || prev.length() < 3) {
                                prev = "0";
                            } else {
                                prev = prev.replaceAll(",|\\.", "");
                            }
                            br.postPage("http://service.jdownloader.org/tools/s.php", "v=" + JDUtilities.getRevision().replaceAll(",|\\.", "") + "&p=" + prev + "&os=" + os + "&tz=" + Encoding.urlEncode(tz));
                            JDUtilities.getConfiguration().setProperty(Configuration.PARAM_UPDATE_VERSION, JDUtilities.getRevision());
                            JDUtilities.getConfiguration().save();
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }).start();
    }

    public void init() {
        initBrowser();

    }

    public void initBrowser() {
        Browser.setLogger(JDLogger.getLogger());

        Browser.setGlobalReadTimeout(SubConfiguration.getConfig("DOWNLOAD").getIntegerProperty(Configuration.PARAM_DOWNLOAD_READ_TIMEOUT, 100000));
        Browser.setGlobalConnectTimeout(SubConfiguration.getConfig("DOWNLOAD").getIntegerProperty(Configuration.PARAM_DOWNLOAD_CONNECT_TIMEOUT, 100000));

        if (SubConfiguration.getConfig("DOWNLOAD").getBooleanProperty(Configuration.USE_PROXY, false)) {

            String host = SubConfiguration.getConfig("DOWNLOAD").getStringProperty(Configuration.PROXY_HOST, "");
            int port = SubConfiguration.getConfig("DOWNLOAD").getIntegerProperty(Configuration.PROXY_PORT, 8080);
            String user = SubConfiguration.getConfig("DOWNLOAD").getStringProperty(Configuration.PROXY_USER, "");
            String pass = SubConfiguration.getConfig("DOWNLOAD").getStringProperty(Configuration.PROXY_PASS, "");
            if (host.trim().equals("")) {
                JDLogger.getLogger().warning("Proxy disabled. No host");
                SubConfiguration.getConfig("DOWNLOAD").setProperty(Configuration.USE_PROXY, false);
                return;
            }

            JDProxy pr = new JDProxy(Proxy.Type.HTTP, host, port);

            if (user != null && user.trim().length() > 0) {
                pr.setUser(user);
            }
            if (pass != null && pass.trim().length() > 0) {
                pr.setPass(pass);
            }
            Browser.setGlobalProxy(pr);

        }
        if (SubConfiguration.getConfig("DOWNLOAD").getBooleanProperty(Configuration.USE_SOCKS, false)) {

            String user = SubConfiguration.getConfig("DOWNLOAD").getStringProperty(Configuration.PROXY_USER_SOCKS, "");
            String pass = SubConfiguration.getConfig("DOWNLOAD").getStringProperty(Configuration.PROXY_PASS_SOCKS, "");
            String host = SubConfiguration.getConfig("DOWNLOAD").getStringProperty(Configuration.SOCKS_HOST, "");
            int port = SubConfiguration.getConfig("DOWNLOAD").getIntegerProperty(Configuration.SOCKS_PORT, 1080);
            if (host.trim().equals("")) {
                JDLogger.getLogger().warning("Socks Proxy disabled. No host");
                SubConfiguration.getConfig("DOWNLOAD").setProperty(Configuration.USE_SOCKS, false);
                return;
            }
            JDProxy pr = new JDProxy(Proxy.Type.SOCKS, host, port);

            if (user != null && user.trim().length() > 0) {
                pr.setUser(user);
            }
            if (pass != null && pass.trim().length() > 0) {
                pr.setPass(pass);
            }
            Browser.setGlobalProxy(pr);
        }
        Browser.init();
    }

    public void initControllers() {
        DownloadController.getInstance();
        PasswordListController.getInstance();
        DownloadController.getInstance().addListener(PasswordListController.getInstance());
        AccountController.getInstance();
        ByteBufferController.getInstance();
        // LinkGrabberController.getInstance();
    }

    public void initGUI(JDController controller) {
        LookAndFeelController.setUIManager();

        Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EDTEventQueue());
        SwingGui.setInstance(JDGui.getInstance());
        UserIF.setInstance(SwingGui.getInstance());
        controller.addControlListener(SwingGui.getInstance());
    }

    public void initPlugins() {
        try {
            loadPluginForDecrypt();
            loadPluginForHost();
            loadCPlugins();

            loadPluginOptional();

            for (final OptionalPluginWrapper plg : OptionalPluginWrapper.getOptionalWrapper()) {
                if (plg.isLoaded()) {
                    try {
                        if (plg.isEnabled() && !plg.getPlugin().initAddon()) {
                            logger.severe("Error loading Optional Plugin:" + plg.getClassName());
                        }
                    } catch (Throwable e) {
                        logger.severe("Error loading Optional Plugin: " + e.getMessage());
                        JDLogger.exception(e);
                    }
                }
            }
        } catch (Exception e) {
            JDLogger.exception(e);
        }
    }

    public boolean installerWasVisible() {
        return installerVisible;
    }

    public Configuration loadConfiguration() {
        Object obj = JDUtilities.getDatabaseConnector().getData(Configuration.NAME);

        if (obj == null) {
            logger.finest("Fresh install?");
            // File file = JDUtilities.getResourceFile(JDUtilities.CONFIG_PATH);
            // if (file.exists()) {
            // logger.info("Wrapping jdownloader.config");
            // obj = JDIO.loadObject(null, file, Configuration.saveAsXML);
            // logger.finest(obj.getClass().getName());
            // JDUtilities.getDatabaseConnector().saveConfiguration(
            // "jdownloaderconfig",
            // obj);
            // }
        }

        if (!TEST_INSTALLER && obj != null && ((Configuration) obj).getStringProperty(Configuration.PARAM_DOWNLOAD_DIRECTORY) != null) {

            Configuration configuration = (Configuration) obj;
            JDUtilities.setConfiguration(configuration);
            jd.controlling.JDLogger.getLogger().setLevel(configuration.getGenericProperty(Configuration.PARAM_LOGGER_LEVEL, Level.WARNING));
            JDTheme.setTheme(GUIUtils.getConfig().getStringProperty(JDGuiConstants.PARAM_THEME, "default"));

        } else {

            File cfg = JDUtilities.getResourceFile("config");
            if (!cfg.exists()) {

                if (!cfg.mkdirs()) {
                    System.err.println("Could not create configdir");
                    return null;
                }
                if (!cfg.canWrite()) {
                    System.err.println("Cannot write to configdir");
                    return null;
                }
            }
            Configuration configuration = new Configuration();
            JDUtilities.setConfiguration(configuration);
            jd.controlling.JDLogger.getLogger().setLevel(configuration.getGenericProperty(Configuration.PARAM_LOGGER_LEVEL, Level.WARNING));
            JDTheme.setTheme(GUIUtils.getConfig().getStringProperty(JDGuiConstants.PARAM_THEME, "default"));

            JDUtilities.getDatabaseConnector().saveConfiguration(Configuration.NAME, JDUtilities.getConfiguration());
            installerVisible = true;
            JDUtilities.getController().fireControlEvent(new ControlEvent(this, SplashScreen.SPLASH_FINISH));
            /**
             * Workaround to enable JGoodies for MAC oS
             */

            LookAndFeelController.setUIManager();
            Installer inst = new Installer();

            if (!inst.isAborted()) {

                File home = JDUtilities.getResourceFile(".");
                if (home.canWrite() && !JDUtilities.getResourceFile("noupdate.txt").exists()) {

                    // try {
                    // new WebUpdate().doWebupdate(true);
                    // JDUtilities.getConfiguration().save();
                    // JDUtilities.getDatabaseConnector().shutdownDatabase();
                    // logger.info(JDUtilities.runCommand("java", new String[] {
                    // "-jar", "jdupdate.jar", "/restart", "/rt" +
                    // JDUtilities.RUNTYPE_LOCAL_JARED },
                    // home.getAbsolutePath(), 0));
                    // System.exit(0);
                    // } catch (Exception e) {
                    // jd.controlling.JDLogger.getLogger().log(java.util.logging.
                    // Level.SEVERE,"Exception occurred",e);
                    // // System.exit(0);
                    // }

                }
                if (!home.canWrite()) {
                    logger.severe("INSTALL abgebrochen");

                    UserIO.getInstance().requestMessageDialog(JDL.L("installer.error.noWriteRights", "Error. You do not have permissions to write to the dir"));

                    JDIO.removeDirectoryOrFile(JDUtilities.getResourceFile("config"));
                    System.exit(1);
                }

            } else {
                logger.severe("INSTALL abgebrochen2");

                UserIO.getInstance().requestMessageDialog(JDL.L("installer.abortInstallation", "Error. User aborted installation."));

                JDIO.removeDirectoryOrFile(JDUtilities.getResourceFile("config"));
                System.exit(0);

            }
        }

        return JDUtilities.getConfiguration();
    }

    public void loadCPlugins() {

        new CPluginWrapper("ccf", "C", ".+\\.ccf");
        new CPluginWrapper("rsdf", "R", ".+\\.rsdf");
        new CPluginWrapper("dlc", "D", ".+\\.dlc");
        new CPluginWrapper("metalink", "MetaLink", ".+\\.metalink");
    }

    public void loadPluginForDecrypt() {
        new DecryptPluginWrapper("Serienjunkies.org", "Serienjunkies", "http://[\\w\\.]*?serienjunkies\\.org.*(rc[_-]|rs[_-]|nl[_-]|u[tl][_-]|ff[_-]|p=[\\d]+|cat=[\\d]+).*");
        new DecryptPluginWrapper("jdloader", "JDLoader", "(jdlist://.+)|((dlc|rsdf|ccf)://.*/.+)", PluginWrapper.LOAD_ON_INIT);
        new DecryptPluginWrapper("charts4you.org", "Charts4You", "http://[\\w\\.]*?charts4you\\.org/\\?id=\\d+");
        new DecryptPluginWrapper("alpha-link.eu", "AlphaLink", "http://[\\w\\.]*?alpha\\-link\\.eu/\\?id=[a-fA-F0-9]+");
        new DecryptPluginWrapper("protectbox.in", "ProtectBoxIn", "http://[\\w\\.]*?protectbox\\.in/.*");
        new DecryptPluginWrapper("animea.net", "AnimeANet", PluginPattern.DECRYPTER_ANIMEANET_PLUGIN);
        new DecryptPluginWrapper("anime-loads.org", "AnimeLoadsorg", "http://[\\w\\.]*?anime-loads\\.org/crypt.php\\?cryptid=[\\w]+|http://[\\w\\.]*?anime-loads\\.org/page.php\\?id=[0-9]+");
        new DecryptPluginWrapper("bat5.com", "URLCash", "http://.+bat5\\.com");
        new DecryptPluginWrapper("counterstrike.de", "CounterstrikeDe", "http://[\\w\\.]*?4players\\.de/\\S*/download/[0-9]+/([01]/)?index\\.html?");
        new DecryptPluginWrapper("googlegroups.com", "GoogleGroups", "http://groups.google.com/group/[^/]+/files/?");
        new DecryptPluginWrapper("best-movies.us", "BestMovies", "http://crypt\\.(best-movies\\.us|capcrypt\\.info)/go\\.php\\?id\\=\\d+");
        new DecryptPluginWrapper("blog-xx.net", "BlogXXNet", "http://[\\w\\.]*?blog-xx\\.net/wp/(.*?)/");
        new DecryptPluginWrapper("bm4u.in", "Bm4uin", "http://[\\w\\.]*?bm4u\\.in/index\\.php\\?do=show_download&id=\\d+");
        new DecryptPluginWrapper("cine.to", "CineTo", "http://[\\w\\.]*?cine\\.to/index\\.php\\?do=show_download&id=[\\w]+|http://[\\w\\.]*?cine\\.to/index\\.php\\?do=protect&id=[\\w]+|http://[\\w\\.]*?cine\\.to/pre/index\\.php\\?do=show_download&id=[\\w]+|http://[\\w\\.]*?cine\\.to/pre/index\\.php\\?do=protect&id=[\\w]+");
        new DecryptPluginWrapper("clipfish.de", "ClipfishDe", "http://[\\w\\.]*?clipfish\\.de/(.*?channel/\\d+/video/\\d+|video/\\d+(/.+)?)");
        new DecryptPluginWrapper("collectr.net", "Collectr", "http://[\\w\\.]*?collectr\\.net/(out/(\\d+/)?\\d+|links/\\w+)");
        new DecryptPluginWrapper("crypting.it", "CryptingIt", "http://[\\w\\.]*?crypting\\.it/(s/[\\w]+|index\\.php\\?p=show(usrfolders)?(&user=.+)?&id=[\\w]+)");
        new DecryptPluginWrapper("all-stream.info", "AllStreamInfo", "http://[\\w\\.]*?all-stream.info/\\?id=\\d+.*");
        new DecryptPluginWrapper("link-protection.org", "LinkProtectionOrg", "http://[\\w\\.]*?link-protection\\.org/.*");
        new DecryptPluginWrapper("crystal-warez.in", "CrystalWarezIN", "http://[\\w\\.]*?crystal-(warez|board)\\.in//?(show_download|protect)/.*?\\.html");
        new DecryptPluginWrapper("protector.to", "ProtectorTO", "http://[\\w\\.]*?protector\\.to/.*");
        new DecryptPluginWrapper("newsurl.de", "NewsUrlDe", "http://[\\w\\.]*?newsurl.de/.*");
        new DecryptPluginWrapper("crypt-it.com", "CryptItCom", "(http|ccf)://[\\w\\.]*?crypt-it\\.com/(s|e|d|c)/[\\w]+");
        new DecryptPluginWrapper("cryptlink.ws", "Cryptlinkws", "http://[\\w\\.]*?cryptlink\\.ws/\\?file=[\\w]+|http://[\\w\\.]*?cryptlink\\.ws/crypt\\.php\\?file=[0-9]+");
        new DecryptPluginWrapper("crypt-me.com", "CryptMeCom", "http://[\\w\\.]*?crypt-me\\.com/folder/[\\w]+\\.html");
        new DecryptPluginWrapper("ddl-music.org", "DDLMusicOrg", PluginPattern.DECRYPTER_DDLMSC_PLUGIN);
        new DecryptPluginWrapper("ddl-warez.org", "DDLWarez", "http://[\\w\\.]*?ddl-warez\\.org/detail\\.php\\?id=.+&cat=[\\w]+");
        new DecryptPluginWrapper("3dl.am", "DreiDlAm", PluginPattern.DECRYPTER_3DLAM_PLUGIN);
        new DecryptPluginWrapper("1kh.de", "EinsKhDe", "http://[\\w\\.]*?1kh\\.de/f/[0-9/]+|http://[\\w\\.]*?1kh\\.de/[0-9]+");
        new DecryptPluginWrapper("falinks.com", "FalinksCom", "http://[\\w\\.]*?falinks\\.com/(\\?fa=link&id=\\d+|link/\\d+/?)");
        new DecryptPluginWrapper("filefactory.com", "FileFactoryFolder", "http://[\\w\\.]*?filefactory\\.com(/|//)f/[\\w]+");
        new DecryptPluginWrapper("filer.net", "Filer", "http://[\\w\\.]*?filer.net/folder/.+/.*");
        new DecryptPluginWrapper("freakshare.net", "FreakShareFolder", "http://[\\w\\.]*?freakshare.net/folder/\\d+/");
        new DecryptPluginWrapper("File-Upload.net", "FileUploadnet", "http://[\\w\\.]*?member\\.file-upload\\.net/(.*?)/(.*)");
        new DecryptPluginWrapper("filmatorium.cn", "FilmatoriumCn", "http://[\\w\\.]*?filmatorium\\.cn/\\?p=\\d+");
        new DecryptPluginWrapper("frozen-roms.in", "FrozenRomsIn", "http://[\\w\\.]*?frozen-roms\\.in/(details_[0-9]+|get_[0-9]+_[0-9]+)\\.html");
        new DecryptPluginWrapper("gwarez.cc", "Gwarezcc", "http://[\\w\\.]*?gwarez\\.cc/(\\d+|mirror/\\d+/checked/game/\\d+/|mirror/\\d+/parts/game/\\d+/|download/dlc/\\d+/)");
        new DecryptPluginWrapper("Hider.ath.cx", "HiderAthCx", "http://[\\w\\.]*?hider\\.ath\\.cx/\\d+");
        new DecryptPluginWrapper("hideurl.biz", "Hideurlbiz", "http://[\\w\\.]*?hideurl\\.biz/[\\w]+");

        new DecryptPluginWrapper("hubupload.com", "Hubuploadcom", "http://[\\w\\.]*?hubupload\\.com/files/[\\w]+/[\\w]+/(.*)");
        new DecryptPluginWrapper("iload.to", "ILoadTo", "http://iload\\.to/go/\\d+/|http://iload\\.to/(view|title|release)/.*?/");
        new DecryptPluginWrapper("imagefap.com", "ImagefapCom", "http://[\\w\\.]*?imagefap\\.com/(gallery\\.php\\?gid=.+|gallery/.+)");
        new DecryptPluginWrapper("joke-around.org", "JokeAroundOrg", "http://[\\w\\.]*?joke-around\\.org/.+");
        new DecryptPluginWrapper("knoffl.com", "KnofflCom", "http://[\\w\\.]*?knoffl\\.com/(u/[\\w-]+|[\\w-]+)");
        new DecryptPluginWrapper("LinkBank.eu", "LinkBankeu", "http://[\\w\\.]*?linkbank\\.eu/show\\.php\\?show=\\d+");
        new DecryptPluginWrapper("linkbase.biz", "LinkbaseBiz", "http://[\\w\\.]*?linkbase\\.biz/\\?v=[\\w]+");
        new DecryptPluginWrapper("linkbucks.com", "LinkBucks", "http://[\\w\\.]*?linkbucks\\.com(/link/[0-9a-zA-Z]+(/\\d+)?)?");
        new DecryptPluginWrapper("qvvo.com", "LinkBucks", "http://[\\w\\.]*?qvvo\\.com(/link/[0-9a-zA-Z]+(/\\d+)?)?");
        new DecryptPluginWrapper("baberepublic.com", "LinkBucks", "http://[\\w\\.]*?baberepublic\\.com(/link/[0-9a-zA-Z]+(/\\d+)?)?");
        new DecryptPluginWrapper("blahetc.com", "LinkBucks", "http://[\\w\\.]*?blahetc\\.com(/link/[0-9a-zA-Z]+(/\\d+)?)?");
        new DecryptPluginWrapper("linkcrypt.ws", "LinkCryptWs", "http://[\\w\\.]*?linkcrypt\\.ws/dir/[\\w]+");
        new DecryptPluginWrapper("linkprotect.in", "LinkProtectIn", "http://[\\w\\.]*?linkprotect\\.in/index.php\\?site=folder&id=[\\w]{1,50}");
        new DecryptPluginWrapper("linkcrypt.com", "LinkcryptCom", "http://[\\w\\.]*?linkcrypt\\.com/[a-zA-Z]-[\\w]+");
        new DecryptPluginWrapper("sealed.in", "SealedIn", "http://[\\w\\.]*?sealed\\.in/[a-zA-Z]-[\\w]+");
        new DecryptPluginWrapper("link-protector.com", "LinkProtectorCom", "http://[\\w\\.]*?link-protector\\.com/(x-)?\\d+");
        new DecryptPluginWrapper("linkr.at|rapidblogger.com", "LinkrAt", "http://[\\w\\.]*?(linkr\\.at/\\?p=|rapidblogger\\.com/link/)\\w+");
        new DecryptPluginWrapper("linksafe.ws", "LinkSafeWs", "http://[\\w\\.]*?linksafe\\.ws/files/[\\w]{4}-[\\d]{5}-[\\d]");
        new DecryptPluginWrapper("Linksave.in", "LinksaveIn", "http://[\\w\\.]*?linksave\\.in/(view.php\\?id=)?[\\w]+");
        new DecryptPluginWrapper("link-share.org", "LinkShareOrg", "http://[\\w\\.]*?link-share\\.org/view.php\\?url=[\\w]{32}");
        new DecryptPluginWrapper("linkshield.com", "Linkshield", "http://[\\w\\.]*?linkshield\\.com/[sc]/[\\d]+_[\\d]+");
        new DecryptPluginWrapper("lix.in", "Lixin", "http://[\\w\\.]*?lix\\.in/[-]{0,1}[\\w]{6,10}");

        new DecryptPluginWrapper("mediafire.com", "MediafireFolder", "http://[\\w\\.]*?mediafire\\.com/(\\?sharekey=.+)");
        new DecryptPluginWrapper("redirect.musicalmente.info", "MusicalmenteInfo", "http://[\\w\\.]*?redirect\\.musicalmente\\.info/.+");
        new DecryptPluginWrapper("music-base.ws", "MusicBaseWs", "http://[\\w\\.]*?music-base\\.ws/dl\\.php.*?c=[\\w]+");
        new DecryptPluginWrapper("myref.de", "MyRef", "http://[\\w\\.]*?myref\\.de(/){0,1}\\?\\d{0,10}");
        new DecryptPluginWrapper("metalinker.org", "MetaLink", "http://[\\d\\w\\.:\\-@]*/.*?\\.metalink");
        new DecryptPluginWrapper("myup.cc", "Myupcc", "http://[\\w\\.]*?myup\\.cc/link-[\\w]+\\.html");
        new DecryptPluginWrapper("myvideo.de", "MyvideoDe", "http://[\\w\\.]*?myvideo\\.de/watch/[0-9]+/");
        new DecryptPluginWrapper("netfolder.in", "NetfolderIn", "http://[\\w\\.]*?netfolder\\.in/folder\\.php\\?folder_id\\=[\\w]{7}|http://[\\w\\.]*?netfolder\\.in/[\\w]{7}/.*?");
        new DecryptPluginWrapper("newzfind.com", "NewzFindCom", "http://[\\w\\.]*?newzfind\\.com/(video|music|games|software|mac|graphics|unix|magazines|e-books|xxx|other)/.+");
        new DecryptPluginWrapper("outlinkr.com", "OutlinkrCom", "http://[\\w\\.]*?outlinkr\\.com/(files|cluster)/[0-9]+/.+");
        new DecryptPluginWrapper("1gabba.com", "OneGabbaCom", "http://[\\w\\.]*?1gabba\\.com/node/[\\d]{4}");
        new DecryptPluginWrapper("Paylesssofts.net", "PaylesssoftsNet", "http://[\\w\\.]*?paylesssofts\\.net/((rs/\\?id\\=)|(\\?))[\\w]+");
        /* Protect.Tehparadox.com: old links still work */
        new DecryptPluginWrapper("Protect.Tehparadox.com", "ProtectTehparadoxcom", "http://[\\w\\.]*?protect\\.tehparadox\\.com/[\\w]+\\!");
        new DecryptPluginWrapper("raidrush.org", "RaidrushOrg", "http://[\\w\\.]*?raidrush\\.org/ext/\\?fid\\=[\\w]+");
        new DecryptPluginWrapper("rapidfolder.com", "RapidFolderCom", "http://[\\w\\.]*?rapidfolder\\.com/\\?\\w+");
        new DecryptPluginWrapper("rapidlayer.in", "Rapidlayerin", "http://[\\w\\.]*?rapidlayer\\.in/go/[\\w]+");
        new DecryptPluginWrapper("rapidsafe.de", "RapidsafeDe", "http://.+rapidsafe\\.de");
        new DecryptPluginWrapper("rapidsafe.net", "Rapidsafenet", "http://[\\w\\.]*?rapidsafe\\.net/r.-?[\\w]{11}/.*");
        new DecryptPluginWrapper("rapidshare.com", "RapidshareComFolder", "http://[\\w\\.]*?rapidshare.com/users/.+");
        new DecryptPluginWrapper("rapidshare.mu", "RapidshareMu", "http://[\\w\\.]*?rapidshare.mu/[\\w]+");
        new DecryptPluginWrapper("Rapidshark.net", "rapidsharknet", "http://[\\w\\.]*?rapidshark\\.net/(safe\\.php\\?id=)?.+");
        new DecryptPluginWrapper("rapidspread.com", "RapidSpreadCom", "http://[\\w\\.]*?rapidspread\\.com/file\\.jsp\\?id=\\w+");
        new DecryptPluginWrapper("r-b-a.de", "RbaDe", "http://[\\w\\.]*?r-b-a\\.de/(index\\.php\\?ID=4101&(amp;)?BATTLE=\\d+(&sid=\\w+)?)|http://[\\w\\.]*?r-b-a\\.de/index\\.php\\?ID=4100(&direction=last)?&MEMBER=\\d+(&sid=\\w+)?");
        new DecryptPluginWrapper("Redirect Services", "Redirecter", PluginPattern.decrypterPattern_Redirecter_Plugin());
        new DecryptPluginWrapper("relink.us", "RelinkUs", "http://[\\w\\.]*?relink\\.us/(go\\.php\\?id=[\\w]+|f/[\\w]+)");
        new DecryptPluginWrapper("relinka.net", "RelinkaNet", "http://[\\w\\.]*?relinka\\.net/folder/[a-z0-9]{8}-[a-z0-9]{4}");
        new DecryptPluginWrapper("rlslog.net", "Rlslog", "(http://[\\w\\.]*?rlslog\\.net(/.+/.+/#comments|/.+/#comments|/.+/.*))");
        new DecryptPluginWrapper("zerosec.ws", "ZeroSecWs", "(http://[\\w\\.]*?zerosec\\.ws(/.+/.+/#comments|/.+/#comments|/.+/.*))");
        new DecryptPluginWrapper("RnB4U.in", "RnB4Uin", "http://[\\w\\.]*?rnb4u\\.in/download\\.php\\?action=kategorie&kat_id=\\d+|http://[\\w\\.]*?rnb4u\\.in/download\\.php\\?action=popup&kat_id=\\d+&fileid=\\d+");
        new DecryptPluginWrapper("rock-house.in", "RockHouseIn", "http://[\\w\\.]*?rock-house\\.in/warez/warez_download\\.php\\?id=\\d+");
        new DecryptPluginWrapper("romhustler.net", "RomHustlerNet", "(http://[\\w\\.]*?romhustler\\.net/rom/.*?/\\d+/.+)|(/rom/.*?/\\d+/.+)");
        new DecryptPluginWrapper("roms.zophar.net", "RomsZopharNet", "http://[\\w\\.]*?roms\\.zophar\\.net/(.+)/(.+\\.7z)");
        new DecryptPluginWrapper("romscentral.com", "RomscentralCom", "(http://[\\w\\.]*?romscentral\\.com/(.+)/(.+\\.htm))|(onclick=\"return popitup\\('(.+\\.htm)'\\))", PluginWrapper.ACCEPTONLYSURLSFALSE);
        new DecryptPluginWrapper("rs.hoerbuch.in", "RsHoerbuchin", "http://rs\\.hoerbuch\\.in/com-[\\w]{11}/.*|http://rs\\.hoerbuch\\.in/de-[\\w]{11}/.*|http://rs\\.hoerbuch\\.in/u[\\w]{6}.html");
        new DecryptPluginWrapper("rs-layer.com", "RsLayerCom", "http://[\\w\\.]*?rs-layer\\.com/(.+)\\.html");
        new DecryptPluginWrapper("rsprotect.com", "RsprotectCom", "http://[\\w\\.]*?rsprotect\\.com/r[sc]-[\\w]{11}/.*");
        new DecryptPluginWrapper("rs-protect.freehoster.ch", "Rsprotectfreehosterch", "http://[\\w\\.]*?rs-protect\\.freehoster\\.ch/r[sc]-[\\w]{11}/.*");
        new DecryptPluginWrapper("rs.xxx-blog.org", "RsXXXBlog", "http://[\\w\\.]*?xxx-blog\\.org/[\\w]{1,4}-[\\w]{10,40}/.*");
        new DecryptPluginWrapper("saug.us", "SAUGUS", "http://[\\w\\.]*?saug\\.us/folder.?-[\\w\\-]{30,50}\\.html|http://[\\w\\.]*?saug\\.us/go.+\\.php");
        new DecryptPluginWrapper("save.raidrush.ws", "SaveRaidrushWs", "http://[\\w\\.]*?save\\.raidrush\\.ws/\\?id\\=[\\w]+");
        new DecryptPluginWrapper("scum.in", "ScumIn", "http://[\\w\\.]*?scum\\.in/index\\.php\\?id=\\d+");
        new DecryptPluginWrapper("sdx.cc", "SdxCc", "http://[\\w\\.]*?sdx\\.cc/infusions/(pro_download_panel|user_uploads)/download\\.php\\?did=\\d+");
        new DecryptPluginWrapper("secured.in", "Secured", "http://[\\w\\.]*?secured\\.in/download-[\\d]+-[\\w]{8}\\.html");
        new DecryptPluginWrapper("sexuria.com", "Sexuriacom", "http://[\\w\\.]*?sexuria\\.com/Pornos_Kostenlos_.+?_(\\d+)\\.html|http://[\\w\\.]*?sexuria\\.com/dl_links_\\d+_(\\d+)\\.html|http://[\\w\\.]*?sexuria\\.com/out.php\\?id=([0-9]+)&part=[0-9]+&link=[0-9]+");
        new DecryptPluginWrapper("sharebank.ws", "SharebankWs", "http://[\\w\\.]*?(mygeek|sharebank)\\.ws/\\?(v|go)=[\\w]+");
        new DecryptPluginWrapper("sharebee.com", "ShareBeeCom", "http://[\\w\\.]*?sharebee\\.com/[\\w]{8}");
        new DecryptPluginWrapper("spiegel.de", "SpiegelDe", "(http://[\\w\\.]*?spiegel\\.de/video/video-\\d+.html|http://[\\w\\.]*?spiegel\\.de/fotostrecke/fotostrecke-\\d+(-\\d+)?.html)");
        new DecryptPluginWrapper("Stealth.to", "Stealth", "http://[\\w\\.]*?stealth\\.to/(\\?id\\=[\\w]+|index\\.php\\?id\\=[\\w]+|\\?go\\=captcha&id=[\\w]+)|http://[\\w\\.]*?stealth\\.to/folder/[\\w]+");
        new DecryptPluginWrapper("technorocker.info", "TechnorockerInfo", "http://[\\w\\.]*?technorocker\\.info/download/[0-9]+/.*");
        new DecryptPluginWrapper("usercash.com", "UserCashCom", "http://[\\w\\.]*?usercash\\.com/");
        new DecryptPluginWrapper("Underground CMS", "UCMS", PluginPattern.decrypterPattern_UCMS_Plugin());
        new DecryptPluginWrapper("uploadjockey.com", "UploadJockeycom", "http://[\\w\\.]*?uploadjockey\\.com/download/[\\w]+/(.*)");
        new DecryptPluginWrapper("up.picoasis.net", "UpPicoasisNet", "http://up\\.picoasis\\.net/[\\d]+");
        new DecryptPluginWrapper("urlcash.net", "URLCash", "(http://[\\w\\.]*?" + PluginPattern.URLCASH + "/.+)|(http://[\\w\\-]{5,16}\\." + PluginPattern.URLCASH + ")");
        new DecryptPluginWrapper("urlshield.net", "UrlShieldnet", "http://[\\w\\.]*?urlshield\\.net/l/[\\w]+");
        new DecryptPluginWrapper("uu.canna.to", "UUCannaTo", "http://[uu\\.canna\\.to|85\\.17\\.36\\.224]+/cpuser/links\\.php\\?action=[cp_]*?popup&kat_id=[\\d]+&fileid=[\\d]+");
        new DecryptPluginWrapper("vetax.in", "VetaXin", "http://[\\w\\.]*?vetax\\.in/view/\\d+|http://[\\w\\.]*?vetax\\.in/(dload|mirror)/[\\w]+");
        new DecryptPluginWrapper("web06.de", "Web06de", "http://[\\w\\.]*?web06\\.de/\\?user=\\d+site=(.*)");
        new DecryptPluginWrapper("wii-reloaded.info", "Wiireloaded", "http://[\\w\\.]*?wii-reloaded\\.(info|org)(/protect)?/get\\.php\\?i=.+");
        new DecryptPluginWrapper("chaoz.ws", "Woireless6xTo", "http://[\\w\\.]*?chaoz\\.ws/woireless/page/album_\\d+\\.html");
        new DecryptPluginWrapper("Wordpress Parser", "Wordpress", PluginPattern.decrypterPattern_Wordpress_Plugin());
        new DecryptPluginWrapper("xaili.com", "Xailicom", "http://[\\w\\.]*?xaili\\.com/\\?site=protect&id=[0-9]+");
        new DecryptPluginWrapper("xenonlink.net", "XenonLinkNet", "http://[\\w\\.]*?xenonlink\\.net/");
        new DecryptPluginWrapper("xink.it", "XinkIt", "http://[\\w\\.]*?xink\\.it/f-[\\w]+");
        new DecryptPluginWrapper("xlice.net", "XliceNet", "http://[\\w\\.]*?xlice\\.net/download/[a-z0-9]+");
        new DecryptPluginWrapper("xrl.us", "XrlUs", "http://[\\w\\.]*?xrl\\.us/[\\w]+");
        new DecryptPluginWrapper("xup.in", "XupInFolder", "http://[\\w\\.]*?xup\\.in/a,[0-9]+(/.+)?(/(list|mini))?");
        new DecryptPluginWrapper("youporn.com", "YouPornCom", "http://[\\w\\.]*?youporn\\.com/watch/\\d+/?.+/?");
        new DecryptPluginWrapper("yourfiles.biz", "YourFilesBizFolder", "http://[\\w\\.]*?yourfiles\\.biz/.*/folders/[0-9]+/.+\\.html");
        new DecryptPluginWrapper("youtube.com", "YouTubeCom", "http://[\\w\\.]*?youtube\\.com/(watch\\?v=[a-z-_A-Z0-9]+|view_play_list\\?p=[a-z-_A-Z0-9]+(.*?page=\\d+)?)");
        new DecryptPluginWrapper("megaupload.com folder", "MegauploadComFolder", "http://[\\w\\.]*?megaupload\\.com/.*?\\?f=[\\w]+");
        new DecryptPluginWrapper("rsmonkey.com", "RsMonkeyCom", "http://[\\w\\.]*?rsmonkey\\.com/\\d+");
        new DecryptPluginWrapper("savefile.com Project", "SavefileComProject", "http://[\\w\\.]*?savefile\\.com/projects/[0-9]+");
        new DecryptPluginWrapper("badongo.com", "BadongoCom", "http://[\\w\\.]*?badongo\\.com/.*(file|vid|audio)/[0-9]+");
        new DecryptPluginWrapper("wrzuta.pl", "WrzutaPl", "http://[\\w\\.]*?wrzuta\\.pl/katalog/\\w+.+");
        new DecryptPluginWrapper("rs43.com", "Rs43Com", "http://[\\w\\.]*?rs43\\.com/(Share\\.Mirror\\.Service/\\?|\\?)/.+");
        new DecryptPluginWrapper("relink-it.com", "RelinkItCom", "http://[\\w\\.]*?relink-it\\.com(/\\w\\w/|/)\\?.+");
        new DecryptPluginWrapper("protectlinks.com", "ProtectLinksCom", "http://[\\w\\.]*?protectlinks\\.com/\\d+");
        new DecryptPluginWrapper("remixshare.com", "RemixShareComFolder", "http://[\\w\\.]*?remixshare\\.com/container/\\?id=[a-z0-9].+");
        new DecryptPluginWrapper("zero10.us", "Zero10Us", "http://[\\w\\.]*?zero10\\.us/\\d+");
        new DecryptPluginWrapper("h-link.us", "Zero10Us", "http://[\\w\\.]*?h-link\\.us/\\d+");
        new DecryptPluginWrapper("jamendo.com", "JamendoCom", "http://[\\w\\.]*?jamendo\\.com/.?.?/?(album/\\d+|artist/.+)");
        new DecryptPluginWrapper("fdnlinks.com", "FDNLinksCom", "http://[\\w\\.]*?fdnlinks\\.com/link/[\\w]+");
        new DecryptPluginWrapper("sprezer.com", "SprezerCom", "http://[\\w\\.]*?sprezer\\.com/file-.+");
        new DecryptPluginWrapper("sharebase.to folder", "ShareBaseToFolder", "http://[\\w\\.]*?sharebase\\.to/ordner/.+");
        new DecryptPluginWrapper("linkbee.com", "LinkBeeCom", "http://[\\w\\.]*?linkbee\\.com/[\\w]+");
        new DecryptPluginWrapper("superuploader.net", "SuperUploaderNet", "http://[\\w\\.]*?superuploader\\.net/.*?\\.html");
        new DecryptPluginWrapper("easy-share.com", "EasyShareFolder", "http://[\\w\\.]*?easy-share\\.com/f/\\d+/");
        new DecryptPluginWrapper("mp3link.org", "Mp3LinkOrg", "http://[\\w\\.]*?mp3link\\.org/.*?/(song|album).+");
        new DecryptPluginWrapper("4shared.com", "FourSharedFolder", "http://[\\w\\.]*?4shared\\.com/dir/\\d+/[\\w]+/?");
        new DecryptPluginWrapper("Hex.io", "HexIo", "http://[\\w\\.]*?hex\\.io/[\\w]+");
        new DecryptPluginWrapper("HyperLinkCash.com", "HyperLinkCashCom", "http://[\\w\\.]*?hyperlinkcash\\.com/link\\.php\\?r=[\\w%]+(&k=[\\w%]+)?");
        new DecryptPluginWrapper("rom-news.org", "RomNewsOrg", "http://[\\w\\.]*?download\\.rom-news\\.org/[\\w]+");
        new DecryptPluginWrapper("quicklink.me", "QuickLinkMe", "http://[\\w\\.]*?quicklink\\.me/\\?l=[\\w]+");
        new DecryptPluginWrapper("short.redirect.am", "ShortRedirectAm", "http://short\\.redirect\\.am/\\?[\\w]+");
        new DecryptPluginWrapper("h-url.in", "HurlIn", "http://[\\w\\.]*?h-url\\.in/[\\w]+");
        new DecryptPluginWrapper("takemyfile.com", "TakemyfileCom", "http://[\\w\\.]*?(tmf\\.myegy\\.com|takemyfile.com)/(.*?id=)?\\d+");
        new DecryptPluginWrapper("raubkopierer.ws", "RaubkopiererWs", "http://[\\w\\.]*?raubkopierer\\.(ws|cc)/\\w+/[\\w/]*?\\d+/.+");
        new DecryptPluginWrapper("yourref.de", "YourRefDe", "http://[\\w\\.]*?yourref\\.de/\\?\\d+");
        new DecryptPluginWrapper("pspisos.org", "PspIsosOrg", "http://[\\w\\.]*?pspisos\\.org/(d\\d\\d?wn|d3741l5)/.+(/\\d+)?");
        new DecryptPluginWrapper("fileducky.com", "Fileducky", "http://[\\w\\.]*?fileducky\\.com/[\\w]+/?");
        new DecryptPluginWrapper("megarotic.com", "MegaRoticCom", "http://[\\w\\.]*?(megarotic|sexuploader)\\.com/.*?(\\?|&)d=[\\w]+");
        new DecryptPluginWrapper("foxlink.info", "FoxLinkInfo", "http://[\\w\\.]*?(foxlink|viplink|zero10)\\.info/\\d+");
        new DecryptPluginWrapper("referhush.com", "ReferHushCom", "http://[\\w\\.]*?referhush\\.com/\\?rh=[a-f0-9]+");
        new DecryptPluginWrapper("amigura.co.uk", "AmiguraCoUk", "http://[\\w\\.]*?amigura\\.co\\.uk/(s\\d+|send_file)\\.php\\?d=(\\d+(-|/)[A-Z0-9]+(-|/)\\d+(-|/)|\\d+/).+");
        new DecryptPluginWrapper("hotfile.com", "HotfileCom", "http://[\\w\\.]*?hotfile\\.com/list/\\d+/[\\w]+");
        new DecryptPluginWrapper("adf.ly", "AdfLy", "http://[\\w\\.]*?adf\\.ly/[\\w]+");
        new DecryptPluginWrapper("uploadr.eu", "UploadrEu", "http://[\\w\\.]*?uploadr\\.eu/(link/[\\w]+|folder/\\d+/)");
        new DecryptPluginWrapper("seriousurls.com", "LinkBucks", "http://[\\w\\.]*?seriousurls\\.com(/link/[0-9a-fA-F]+(/\\d+)?)?");
        new DecryptPluginWrapper("thesegalleries.com", "LinkBucks", "http://[\\w\\.]*?thesegalleries\\.com(/link/[0-9a-fA-F]+(/\\d+)?)?");
        new DecryptPluginWrapper("ubucks.net", "LinkBucks", "http://[\\w\\.]*?ubucks\\.net(/link/[0-9a-fA-F]+(/\\d+)?)?");
        new DecryptPluginWrapper("seriousfiles.com", "LinkBucks", "http://[\\w\\.]*?seriousfiles\\.com(/link/[0-9a-fA-F]+(/\\d+)?)?");
        new DecryptPluginWrapper("viraldatabase.com", "LinkBucks", "http://[\\w\\.]*?viraldatabase\\.com(/link/[0-9a-fA-F]+(/\\d+)?)?");
        new DecryptPluginWrapper("placepictures.com", "LinkBucks", "http://[\\w\\.]*?placepictures\\.com(/link/[0-9a-fA-F]+(/\\d+)?)?");
        new DecryptPluginWrapper("urlpulse.net", "LinkBucks", "http://[\\w\\.]*?urlpulse\\.net(/link/[0-9a-fA-F]+(/\\d+)?)?");
        new DecryptPluginWrapper("thesefiles.com", "LinkBucks", "http://[\\w\\.]*?thesefiles\\.com(/link/[0-9a-fA-F]+(/\\d+)?)?");
        new DecryptPluginWrapper("linkgalleries.net", "LinkBucks", "http://[\\w\\.]*?linkgalleries\\.net(/link/[0-9a-fA-F]+(/\\d+)?)?");
        new DecryptPluginWrapper("youfap.com", "LinkBucks", "http://[\\w\\.]*?youfap\\.com(/link/[0-9a-fA-F]+(/\\d+)?)?");
        new DecryptPluginWrapper("realfiles.net", "LinkBucks", "http://[\\w\\.]*?realfiles\\.net(/link/[0-9a-fA-F]+(/\\d+)?)?");
        new DecryptPluginWrapper("urlcut.com", "UrlCutCom", "http://[\\w\\.]*?urlcut\\.com/[0-9a-zA-Z]+");
        new DecryptPluginWrapper("short.3arabforest.com", "ThreeArabForestCom", "http://[\\w\\.]*?short\\.3arabforest\\.com/\\d+");
        new DecryptPluginWrapper("youcrypt.com", "YouCryptCom", "http://[\\w\\.]*?youcrypt\\.com/[0-9a-zA-Z]+\\.html");
        new DecryptPluginWrapper("zomgupload.com", "ZomgUploadCom", "(?!http://[\\w\\.]*?zomgupload\\.com/.+[/0-9a-zA-Z/-]+.html)http://[\\w\\.]*?zomgupload\\.com/.+[/0-9a-zA-Z/-]");
        new DecryptPluginWrapper("rsd-store.com", "RsdStore", "http://[\\w\\.]*?rsd-store\\.com/\\d+\\.html");
        new DecryptPluginWrapper("movietown.info", "MovieTown", "http://[\\w\\.]*?movietown\\.info/index/download\\.php\\?id=\\d+");
        new DecryptPluginWrapper("nareey.com", "NareeyCom", "http://[\\w\\.]*?nareey\\.com/(\\d\\.php\\?\\d+|\\d+/)");
        new DecryptPluginWrapper("q1q1q.com", "Q1Q1QCom", "http://[\\w\\.]*?q1q1q\\.com/\\d+");
        new DecryptPluginWrapper("minyurl.net", "MinyUrlNet", "http://[\\w\\.]*?minyurl\\.net/[^\\s^/]+");
        new DecryptPluginWrapper("linksafe.info", "LinksafeInfo", "http://[\\w\\.]*?linksafe\\.info/[^\\s^/]+");
        new DecryptPluginWrapper("shrunkin.com", "ShrunkinCom", "http://[\\w\\.]*?shrunkin\\.com/.+");
        new DecryptPluginWrapper("go4Down.net", "GoFourDownNet", "http://[\\w\\.]*?(short\\.)?go4down\\.(com|net)/(short/)\\d+");
        new DecryptPluginWrapper("sogood.net", "SoGoodNet", "http://[\\w\\.]*?sogood\\.net/.+");
        new DecryptPluginWrapper("audiobeats.net", "AudioBeatsNet", "http://[\\w\\.]*?audiobeats\\.net/app/(parties|livesets|artists)/show/[0-9a-zA-Z]+");
        new DecryptPluginWrapper("Duckload.com", "DuckLoadCom", "http://[\\w\\.]*?(duckload\\.com|youload\\.to)/(?!(download|divx))[a-zA-Z0-9]+(?!\\.html)$");
        new DecryptPluginWrapper("cryptbox.cc", "CryptBoxCC", "http://[\\w\\.]*?.cryptbox\\.cc/ordner/[0-9a-zA-z]+");
        new DecryptPluginWrapper("depositfiles.com", "DepositFilesCom", "http://?[\\w\\.]*?.depositfiles\\.com/([a-z]+/folders/|folders/).*");
        new DecryptPluginWrapper("gazup.com", "GazUpCom", "http://[\\w\\.]*?.gazup\\.com/.+");
        new DecryptPluginWrapper("anonym.to", "AnonymTo", "http://[\\w\\.]*?anonym\\.to/\\?.+");
        new DecryptPluginWrapper("qooy.com", "QooyCom", "http://[\\w\\.]*?qooy\\.com/files/[0-9A-Z]+/[0-9a-zA-z.]+");
        new DecryptPluginWrapper("free-url.net", "FreeUrlNet", "http://[\\w\\.]*?free-url\\.net/[0-9]+/");
        new DecryptPluginWrapper("box.net", "BoxNet", "http://www\\.box\\.net/shared/(\\w+\\b(?<!\\bstatic))(/rss\\.xml|#\\w*)?");
        new DecryptPluginWrapper("Szort.pl", "SzortPl", "http://[\\w\\.]*(tini\\.us|justlink\\.us|poourl\\.com|szort\\.pl)/.+");
        new DecryptPluginWrapper("Zoodl.com", "ZoodlCom", "http://[\\w\\.]*?zoodl\\.com/.+");
        new DecryptPluginWrapper("divshare.com", "DivShareComFolder", "http://[\\w\\.]*?divshare\\.com/folder/[0-9a-zA-z|-]+");
        new DecryptPluginWrapper("mega.1280.com", "Mega1280ComFolder", "http://[\\w\\.]*?mega\\.1280\\.com/folder/[A-Z|0-9]+");
        new DecryptPluginWrapper("Snurl.com", "SnurlCom", "http://[\\w\\.]*?(snurl\\.com|snipurl\\.com|sn\\.im|snipr\\.com)/.+");
        new DecryptPluginWrapper("data-loading.com", "DataLoadingComFolder", "http://[\\w\\.]*?data-loading\\.com/.{2}/folders/\\d+/\\w+");
        new DecryptPluginWrapper("filezone.ro", "FileZoneRo", "http://[\\w\\.]*?filezone\\.ro/(public/viewset/\\d+|browse/[a-z]+/[0-9A-Za-z_-]+)");
        new DecryptPluginWrapper("hiddenip.net", "HiddenIPnet", "http://[\\w\\.]*?hiddenip\\.net/.*?q=[0-9A-Za-z|]+.+");
        new DecryptPluginWrapper("deezer.com", "DeezerCom", "http://[\\w\\.]*?deezer.com/.*?music/.+", PluginWrapper.DEBUG_ONLY);
        new DecryptPluginWrapper("file2upload.net", "File2UploadNetFolder", "http://[\\w\\.]*?file2upload\\.(net|com)/folder/[A-Z|0-9]+/");
        new DecryptPluginWrapper("filestube.com", "FileStubeCom", "http://[\\w\\.]*?filestube\\.com/.+");
        new DecryptPluginWrapper("mp3zr.com", "Mp3ZrCom", "http://[\\w\\.]*?mp3zr\\.com/file/[0-9a-zA-Z]+/.+");
        new DecryptPluginWrapper("multiupload.com", "MultiuploadCom", "http://[\\w\\.]*?multiupload\\.com/[0-9A-Z]+");
        new DecryptPluginWrapper("yourfileplace.Com", "YourFilePlaceCom", "http://[\\w\\.]*?yourfileplace\\.com/files/\\d+/.+\\.html");

        // Decrypter from Extern
        new DecryptPluginWrapper("rapidlibrary.com", "RapidLibrary", "http://rapidlibrary\\.com/download_file_i\\.php\\?.+");
    }

    /**
     * Returns a classloader to load plugins (class files); Depending on runtype
     * (dev or local jared) a different classoader is used to load plugins
     * either from installdirectory or from rundirectory
     * 
     * @return
     */
    private static ClassLoader getPluginClassLoader() {
        if (CL == null) {
            try {
                if (JDUtilities.getRunType() == JDUtilities.RUNTYPE_LOCAL_JARED) {
                    CL = new URLClassLoader(new URL[] { JDUtilities.getJDHomeDirectoryFromEnvironment().toURI().toURL(), JDUtilities.getResourceFile("java").toURI().toURL() }, Thread.currentThread().getContextClassLoader());
                } else {
                    CL = Thread.currentThread().getContextClassLoader();
                }
            } catch (MalformedURLException e) {
                JDLogger.exception(e);
            }
        }
        return CL;
    }

    public void loadPluginForHost() {
        try {
            for (Class<?> c : ClassFinder.getClasses("jd.plugins.hoster", getPluginClassLoader())) {
                try {
                    logger.finest("Try to load " + c);
                    if (c != null && c.getAnnotations().length > 0) {
                        HostPlugin help = (HostPlugin) c.getAnnotations()[0];

                        if (help.interfaceVersion() != HostPlugin.INTERFACE_VERSION) {
                            logger.warning("Outdated Plugin found: " + help);
                            continue;
                        }
                        for (int i = 0; i < help.names().length; i++) {
                            try {
                                new HostPluginWrapper(help.names()[i], c.getSimpleName(), help.urls()[i], help.flags()[i], help.revision());
                            } catch (Throwable e) {
                                JDLogger.exception(e);
                            }
                        }
                    }
                } catch (Throwable e) {
                    JDLogger.exception(e);
                }
            }
        } catch (Throwable e) {
            JDLogger.exception(e);
        }
    }

    public void loadPluginOptional() {
        ArrayList<String> list = new ArrayList<String>();
        try {
            for (Class<?> c : ClassFinder.getClasses("jd.plugins.optional", JDUtilities.getJDClassLoader())) {
                try {
                    if (list.contains(c.getName())) {
                        System.out.println("Already loaded:" + c);
                        continue;
                    }
                    if (c.getAnnotations().length > 0) {
                        OptionalPlugin help = (OptionalPlugin) c.getAnnotations()[0];

                        if ((help.windows() && OSDetector.isWindows()) || (help.linux() && OSDetector.isLinux()) || (help.mac() && OSDetector.isMac())) {
                            if (JDUtilities.getJavaVersion() >= help.minJVM() && PluginOptional.ADDON_INTERFACE_VERSION == help.interfaceversion()) {
                                logger.finest("Init PluginWrapper!");
                                new OptionalPluginWrapper(c, help);
                                list.add(c.getName());
                            }
                        }
                    }
                } catch (Throwable e) {
                    JDLogger.exception(e);
                }
            }
        } catch (Throwable e) {
            JDLogger.exception(e);
        }
    }

    public void removeFiles() {
        String[] remove = Regex.getLines(JDIO.getLocalFile(JDUtilities.getResourceFile("outdated.dat")));
        String homedir = JDUtilities.getJDHomeDirectoryFromEnvironment().toString();
        if (remove != null) {
            for (String file : remove) {
                if (file.length() == 0) continue;
                if (!file.matches(".*?" + File.separator + "?\\.+" + File.separator + ".*?")) {
                    File delete = new File(homedir, file);
                    if (JDIO.removeDirectoryOrFile(delete)) logger.warning("Removed " + file);
                }
            }
        }
    }

}
