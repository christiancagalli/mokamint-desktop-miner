package it.univr.mokamintminer.services;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MinerXmlManager {

    private static final String XML_PATH = "miner_storage/miners.xml";

    /**
     * Carico tutti i miner salvati nel file XML, formattato secondo lo schema del nodo.
     */
    public static List<MinerInstance> loadMiners() {
        List<MinerInstance> miners = new ArrayList<>();
        File file = new File(XML_PATH);

        if (!file.exists()) {
            return miners;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);
            document.getDocumentElement().normalize();

            NodeList nodeList = document.getElementsByTagName("miner");

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);

                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;

                    // Lettura dei tag principali del miner
                    String uuid = getTagValue("uuid", element);
                    String uri = getTagValue("uri", element);
                    String plotSizeStr = getTagValue("plot-size", element);
                    String creationTimeStr = getTagValue("creation-time-utc", element);
                    String pubKeyDeadlines = getTagValue("public-key-for-signing-deadlines-base58", element);

                    // Gestione del sotto-nodo <mining-specification>
                    NodeList specList = element.getElementsByTagName("mining-specification");
                    String chainId = "";
                    String sigBlocks = "";
                    String sigDeadlines = "";
                    String pubKeyBlocks = "";
                    String name = "Mokamint Miner"; // Fallback se non c'è il tag name fuori

                    String hashingForDeadlines = "";
                    if (specList != null && specList.getLength() > 0) {
                        Element specElement = (Element) specList.item(0);
                        name = getTagValue("name", specElement);
                        chainId = getTagValue("chain-id", specElement);
                        hashingForDeadlines = getTagValue("hashing-for-deadlines", specElement);
                        sigBlocks = getTagValue("signature-for-blocks", specElement);
                        sigDeadlines = getTagValue("signature-for-deadlines", specElement);
                        pubKeyBlocks = getTagValue("public-key-for-signing-blocks-base58", specElement);
                    }

                    // Costruisco l'istanza dai dati recuperati
                    MinerInstance miner = new MinerInstance(uuid, name, uri);

                    // Mappo i percorsi locali derivandoli dall'UUID
                    miner.setPlotPath("miner_storage/data/" + uuid + ".plot");
                    miner.setPemPath("miner_storage/identities/" + uuid + ".pem");

                    // Sincronizzo il resto dei parametri
                    miner.setChainId(chainId);
                    miner.setHashingForDeadlines(hashingForDeadlines);
                    miner.setSignatureForBlocks(sigBlocks);
                    miner.setSignatureForDeadlines(sigDeadlines);
                    miner.setPublicKeyBlocksBase58(pubKeyBlocks);
                    miner.setPublicKeyDeadlinesBase58(pubKeyDeadlines);

                    if (!creationTimeStr.isEmpty()) {
                        miner.setCreationTimeUtc(Long.parseLong(creationTimeStr));
                    }

                    // Flag di avvio automatico: assente nei vecchi XML -> default true.
                    String activeStr = getTagValue("active", element);
                    miner.setActive(activeStr.isEmpty() || Boolean.parseBoolean(activeStr));

                    miners.add(miner);
                }
            }
        } catch (Exception e) {
            System.err.println("Errore durante il parsing del file XML dei miner: " + e.getMessage());
            e.printStackTrace();
        }

        return miners;
    }

    /**
     * Scrivo i miner rispecchiando l'albero XML usato dal nodo.
     */
    public static void saveMiners(List<MinerInstance> miners) {
        try {
            File file = new File(XML_PATH);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.newDocument();

            Element rootElement = document.createElement("miners");
            document.appendChild(rootElement);

            for (MinerInstance miner : miners) {
                Element minerElement = document.createElement("miner");

                // <uuid>
                addChildElement(document, minerElement, "uuid", miner.getUuid());

                // <mining-specification> (Sotto-albero annidato)
                Element specElement = document.createElement("mining-specification");
                addChildElement(document, specElement, "name", miner.getName());
                addChildElement(document, specElement, "description", "A blockchain with Takamaka smart contracts");
                addChildElement(document, specElement, "chain-id", miner.getChainId());
                String hashing = miner.getHashingForDeadlines();
                addChildElement(document, specElement, "hashing-for-deadlines",
                        (hashing != null && !hashing.isBlank()) ? hashing : "shabal256");
                addChildElement(document, specElement, "signature-for-blocks", miner.getSignatureForBlocks());
                addChildElement(document, specElement, "signature-for-deadlines", miner.getSignatureForDeadlines());
                addChildElement(document, specElement, "public-key-for-signing-blocks-base58", miner.getPublicKeyBlocksBase58());
                minerElement.appendChild(specElement);

                // Altri tag di primo livello del miner
                addChildElement(document, minerElement, "uri", miner.getNodeUri());

                // Ricavo la dimensione reale del file di plot sul disco
                long size = 0;
                File pFile = new File(miner.getPlotPath() != null ? miner.getPlotPath() : "");
                if (pFile.exists()) {
                    size = pFile.length();
                }
                addChildElement(document, minerElement, "plot-size", String.valueOf(size));

                addChildElement(document, minerElement, "public-key-for-signing-deadlines-base58", miner.getPublicKeyDeadlinesBase58());
                addChildElement(document, minerElement, "creation-time-utc", String.valueOf(miner.getCreationTimeUtc()));
                addChildElement(document, minerElement, "active", String.valueOf(miner.isActive()));

                rootElement.appendChild(minerElement);
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(file);
            transformer.transform(source, result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addMiner(MinerInstance newMiner) {
        List<MinerInstance> currentMiners = loadMiners();
        currentMiners.removeIf(m -> m.getUuid().equals(newMiner.getUuid()));
        currentMiners.add(newMiner);
        saveMiners(currentMiners);
    }

    public static void removeMiner(String uuid) {
        List<MinerInstance> currentMiners = loadMiners();
        currentMiners.removeIf(m -> m.getUuid().equals(uuid));
        saveMiners(currentMiners);
    }

    /** Aggiorna nello XML il flag di avvio automatico di un singolo miner. */
    public static void setActive(String uuid, boolean active) {
        List<MinerInstance> currentMiners = loadMiners();
        for (MinerInstance m : currentMiners) {
            if (m.getUuid().equals(uuid)) {
                m.setActive(active);
                break;
            }
        }
        saveMiners(currentMiners);
    }

    private static String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList != null && nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            if (node != null) {
                return node.getTextContent();
            }
        }
        return "";
    }

    private static void addChildElement(Document doc, Element parent, String tagName, String value) {
        Element child = doc.createElement(tagName);
        child.appendChild(doc.createTextNode(value != null ? value : ""));
        parent.appendChild(child);
    }
}