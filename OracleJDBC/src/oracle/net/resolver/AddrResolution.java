package oracle.net.resolver;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import oracle.net.TNSAddress.SOException;
import oracle.net.jndi.JndiAttrs;
import oracle.net.nl.NLException;
import oracle.net.nl.NVFactory;
import oracle.net.nl.NVNavigator;
import oracle.net.nl.NVPair;
import oracle.net.ns.NetException;
import oracle.net.nt.ConnOption;
import oracle.net.nt.ConnStrategy;

public class AddrResolution {
    private ConnStrategy cs;
    private Properties up;
    private static final String default_proxy_rules = "__jdbc__";
    private static final String service_alias_name = "ora-net-service-alias";
    private static final String service_attr_name = "orclnetdescstring";
    private static final int length_of_alias_prefix = 6;
    public static final int DEFAULT_DATABASE_PORT = 1521;
    public static final String DEFAULT_CONNECT_PROTOCOL = "TCP";
    private boolean newSyntax = true;
    public boolean connection_revised = false;
    public boolean connection_redirected = false;
    private String TNSAddress;

    public AddrResolution(String paramString, Properties paramProperties) throws NetException {
        this.up = paramProperties;
        this.TNSAddress = paramString;
        if ((this.up.containsKey("java.naming.provider.url")) || (paramString.startsWith("ldap:"))
                || (paramString.startsWith("ldaps:"))) {
            int i = 0;
            if ((paramString.startsWith("ldap:")) || (paramString.startsWith("ldaps:")))
                if (paramString.indexOf(' ') > 0) {
                    i = 1;
                } else {
                    int j;
                    if ((j = paramString.lastIndexOf('/')) == -1)
                        throw new NetException(124);
                    this.up.put("java.naming.provider.url", paramString.substring(0, j));
                    this.TNSAddress = paramString.substring(j + 1, paramString.length());
                }
            if (i == 0) {
                String[] arrayOfString = new String[1];
                JndiAttrs localJndiAttrs = new JndiAttrs(this.up);
                arrayOfString[0] = "orclnetdescstring";
                Vector localVector = null;
                try {
                    localVector = localJndiAttrs.getAttrs(this.TNSAddress, arrayOfString);
                } finally {
                    localJndiAttrs.close();
                }
                this.TNSAddress = ((String) localVector.firstElement());
                this.connection_revised = true;
            } else {
                processLdapFailoverLoadblance(paramString);
            }
        }
        if (this.up.getProperty("oracle.net.oldSyntax") == "YES")
            this.newSyntax = false;
    }

    private void processLdapFailoverLoadblance(String paramString) throws NetException {
        int i = 0;
        Vector localVector = new Vector(10);
        char[] arrayOfChar = paramString.toCharArray();
        int k = arrayOfChar.length;
        String str1;
        while (i < k) {
            int j = 0;
            for (j = i + 1; (j < k) && (arrayOfChar[j] != ' '); j++)
                ;
            str1 = new String(arrayOfChar, i, j - i);
            if (str1.startsWith("ldap"))
                localVector.addElement(str1);
            else
                throw new NetException(124);
            for (i = j + 1; (i < k) && (arrayOfChar[i] == ' '); i++)
                ;
        }
        if (localVector.size() <= 0)
            throw new NetException(124);
        boolean bool1 = true;
        boolean bool2 = true;
        String str2;
        if (((str2 = this.up.getProperty("oracle.net.ldap_failover")) != null)
                && ((str2.equalsIgnoreCase("OFF")) || (str2.equalsIgnoreCase("FALSE")) || (str2
                        .equalsIgnoreCase("NO"))))
            bool1 = false;
        if (((str2 = this.up.getProperty("oracle.net.ldap_loadbalance")) != null)
                && ((str2.equalsIgnoreCase("OFF")) || (str2.equalsIgnoreCase("FALSE")) || (str2
                        .equalsIgnoreCase("NO"))))
            bool2 = false;
        if (localVector.size() > 1)
            localVector = NavDescriptionList.setActiveChildren(localVector, bool1, bool2);
        StringBuffer localStringBuffer = new StringBuffer();
        int m = localVector.size();
        Hashtable localHashtable = new Hashtable(m);
        for (int n = 0; n < m; n++) {
            str1 = (String) localVector.elementAt(n);
            int i1;
            if ((i1 = str1.lastIndexOf('/')) == -1)
                throw new NetException(124);
            String str4 = str1.substring(0, i1);
            String localObject1 = str1.substring(i1 + 1, str1.length());
            localStringBuffer.append(str4);
            if (n < m - 1)
                localStringBuffer.append(' ');
            localHashtable.put(str4, localObject1);
        }
        String str3 = new String(localStringBuffer);
        this.up.put("java.naming.provider.url", str3);
        JndiAttrs localJndiAttrs = new JndiAttrs(this.up);
        String str4 = localJndiAttrs.getLdapUrlUsed();
        this.TNSAddress = ((String) localHashtable.get(str4));
        Object localObject1 = null;
        String[] arrayOfString = new String[1];
        arrayOfString[0] = "orclnetdescstring";
        try {
            localObject1 = localJndiAttrs.getAttrs(this.TNSAddress, arrayOfString);
        } finally {
            localJndiAttrs.close();
        }
        this.TNSAddress = ((String) ((Vector) localObject1).firstElement());
        this.connection_revised = true;
    }

    public String getTNSAddress() {
        return this.TNSAddress.toUpperCase();
    }

    public ConnOption resolveAndExecute(String paramString) throws NetException, IOException {
        ConnStrategy localConnStrategy = this.cs;
        if (paramString != null) {
            this.cs = new ConnStrategy(this.up);
            if (this.connection_redirected) {
                this.cs.sdu = localConnStrategy.sdu;
                this.cs.tdu = localConnStrategy.tdu;
                this.cs.socketOptions = localConnStrategy.socketOptions;
                this.cs.reuseOpt = true;
                this.connection_redirected = false;
            }
            if (paramString.indexOf(')') == -1) {
                int i;
                if (((i = paramString.indexOf(':')) != -1)
                        && (paramString.indexOf(':', i + 1) != -1)) {
                    resolveSimple(paramString);
                } else {
                    String str = System.getProperty("oracle.net.tns_admin");
                    NameResolver localNameResolver = NameResolverFactory.getNameResolver(str);
                    resolveAddrTree(localNameResolver.resolveName(paramString));
                }
            } else if (this.newSyntax) {
                resolveAddrTree(paramString);
            } else {
                resolveAddr(paramString);
            }
        } else if ((this.cs == null) || (!this.cs.hasMoreOptions())) {
            return null;
        }
        return this.cs.execute();
    }

    private void resolveSimple(String paramString) throws NetException {
        ConnOption localConnOption = new ConnOption();
        int i = 0;
        int j = 0;
        int k = 0;
        if (((i = paramString.indexOf(':')) == -1) || ((j = paramString.indexOf(':', i + 1)) == -1))
            throw new NetException(115);
        if ((k = paramString.indexOf(':', j + 1)) != -1)
            throw new NetException(115);
        try {
            localConnOption.host = paramString.substring(0, i);
            localConnOption.port = Integer.parseInt(paramString.substring(i + 1, j));
            localConnOption.addr = ("(ADDRESS=(PROTOCOL=tcp)(HOST=" + localConnOption.host
                    + ")(PORT=" + localConnOption.port + "))");
            localConnOption.sid = paramString.substring(j + 1, paramString.length());
            String str = "(DESCRIPTION=(CONNECT_DATA=(SID=" + localConnOption.sid
                    + ")(CID=(PROGRAM=)(HOST=__jdbc__)(USER=)))" + "(ADDRESS="
                    + "(PROTOCOL=tcp)(HOST=" + localConnOption.host + ")(PORT="
                    + localConnOption.port + ")))";
            localConnOption.protocol = "TCP";
            localConnOption.conn_data = new StringBuffer(str);
            this.cs.addOption(localConnOption);
        } catch (NumberFormatException localNumberFormatException) {
            throw new NetException(116);
        }
    }

    private void resolveAddr(String paramString) throws NetException {
        if (paramString.startsWith("alias=")) {
            String localObject = paramString;
            paramString = ((String) localObject)
                    .substring(((String) localObject).indexOf("alias=") + 6, ((String) localObject)
                            .length());
        }
        Object localObject = new ConnOption();
        NVFactory localNVFactory = new NVFactory();
        NVNavigator localNVNavigator = new NVNavigator();
        NVPair localNVPair1 = null;
        NVPair localNVPair2 = null;
        try {
            localNVPair1 = localNVNavigator.findNVPairRecurse(localNVFactory
                    .createNVPair(paramString), "CID");
            localNVPair2 = localNVNavigator.findNVPairRecurse(localNVFactory
                    .createNVPair(paramString), "address");
        } catch (NLException localNLException1) {
            System.err.println(localNLException1.getMessage());
        }
        NVPair localNVPair3 = localNVNavigator.findNVPair(localNVPair2, "protocol");
        if (localNVPair3 == null)
            throw new NetException(100);
        ((ConnOption) localObject).protocol = localNVPair3.getAtom();
        if ((!((ConnOption) localObject).protocol.equals("TCP"))
                && (!((ConnOption) localObject).protocol.equals("tcp"))
                && (!((ConnOption) localObject).protocol.equals("SSL"))
                && (!((ConnOption) localObject).protocol.equals("ssl"))
                && (!((ConnOption) localObject).protocol.equals("ANO"))
                && (!((ConnOption) localObject).protocol.equals("ano")))
            throw new NetException(102);
        localNVPair3 = localNVNavigator.findNVPair(localNVPair2, "Host");
        if (localNVPair3 == null)
            throw new NetException(103);
        ((ConnOption) localObject).host = localNVPair3.getAtom();
        localNVPair3 = localNVNavigator.findNVPair(localNVPair2, "Port");
        if (localNVPair3 == null)
            throw new NetException(104);
        ((ConnOption) localObject).port = Integer.parseInt(localNVPair3.getAtom());
        localNVPair3 = localNVNavigator.findNVPair(localNVPair2, "sduSize");
        if (localNVPair3 != null)
            ((ConnOption) localObject).sdu = Integer.parseInt(localNVPair3.getAtom());
        localNVPair3 = localNVNavigator.findNVPair(localNVPair2, "tduSize");
        if (localNVPair3 != null)
            ((ConnOption) localObject).tdu = Integer.parseInt(localNVPair3.getAtom());
        NVPair localNVPair4 = null;
        try {
            localNVPair4 = localNVNavigator.findNVPairRecurse(localNVFactory
                    .createNVPair(paramString), "connect_data");
        } catch (NLException localNLException2) {
            System.err.println(localNLException2.getMessage());
        }
        StringBuffer localStringBuffer = new StringBuffer(paramString);
        ((ConnOption) localObject).conn_data = (localNVPair4 != null ? insertCID(paramString)
                : localStringBuffer);
        ((ConnOption) localObject).addr = ("(ADDRESS=(PROTOCOL=tcp)(HOST="
                + ((ConnOption) localObject).host + ")(PORT=" + ((ConnOption) localObject).port + "))");
        this.cs.addOption((ConnOption) localObject);
    }

    private void resolveAddrTree(String paramString) throws NetException {
        NavSchemaObjectFactory localNavSchemaObjectFactory = new NavSchemaObjectFactory();
        NavServiceAlias localNavServiceAlias = (NavServiceAlias) localNavSchemaObjectFactory
                .create(4);
        try {
            String str = "alias=" + paramString;
            localNavServiceAlias.initFromString(str);
        } catch (NLException localNLException) {
            throw new NetException(501);
        } catch (SOException localSOException) {
            throw new NetException(502, localSOException.getMessage());
        }
        localNavServiceAlias.navigate(this.cs, null);
    }

    private StringBuffer insertCID(String s) throws NetException {
        NVFactory nvfactory = new NVFactory();
        NVNavigator nvnavigator = new NVNavigator();
        StringBuffer stringbuffer = new StringBuffer(2048);
        Object obj = null;
        NVPair nvpair1 = null;
        NVPair nvpair2 = null;
        NVPair nvpair3 = null;
        NVPair nvpair4 = null;
        try {
            NVPair nvpair = nvnavigator.findNVPairRecurse(nvfactory.createNVPair(s), "description");
            nvpair1 = nvnavigator.findNVPairRecurse(nvfactory.createNVPair(s), "address_list");
            nvpair2 = nvnavigator.findNVPairRecurse(nvfactory.createNVPair(s), "address");
            nvpair3 = nvnavigator.findNVPairRecurse(nvfactory.createNVPair(s), "connect_data");
            nvpair4 = nvnavigator.findNVPairRecurse(nvfactory.createNVPair(s), "source_route");
        } catch (NLException nlexception) {
            System.err.println(nlexception.getMessage());
        }
        NVPair nvpair5 = null;
        Object obj1 = null;
        NVPair nvpair7 = null;
        if (nvpair3 != null) {
            nvpair5 = nvnavigator.findNVPair(nvpair3, "SID");
            NVPair nvpair6 = nvnavigator.findNVPair(nvpair3, "CID");
            nvpair7 = nvnavigator.findNVPair(nvpair3, "SERVICE_NAME");
        } else {
            throw new NetException(105);
        }
        if (nvpair5 == null && nvpair7 == null) {
            throw new NetException(106);
        }
        stringbuffer.append("(DESCRIPTION=");
        if (nvpair1 != null && nvpair1.getListSize() > 0) {
            for (int i = 0; i < nvpair1.getListSize(); i++) {
                NVPair nvpair8 = nvpair1.getListElement(i);
                stringbuffer.append(nvpair8.toString());
            }

        } else if (nvpair2 != null) {
            stringbuffer.append(nvpair2.toString());
        } else {
            throw new NetException(107);
        }
        if (nvpair7 != null) {
            stringbuffer.append("(CONNECT_DATA=" + nvpair7.toString()
                    + "(CID=(PROGRAM=)(HOST=__jdbc__)(USER=)))");
        } else {
            stringbuffer.append("(CONNECT_DATA=" + nvpair5.toString()
                    + "(CID=(PROGRAM=)(HOST=__jdbc__)(USER=)))");
        }
        if (nvpair4 != null) {
            stringbuffer.append(nvpair4.toString());
        }
        stringbuffer.append(")");
        return stringbuffer;
    }

    public Properties getUp() {
        return this.up;
    }
}

/*
 * Location: D:\oracle\product\10.2.0\client_1\jdbc\lib\ojdbc14_g.jar Qualified Name:
 * oracle.net.resolver.AddrResolution JD-Core Version: 0.6.0
 */