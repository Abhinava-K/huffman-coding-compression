import java.io.*;
import java.util.*;
import java.nio.file.*;

class HuffmanCoding {

    static class Node implements Serializable {
        char character;
        int frequency;
        Node left, right;

        Node(char character, int frequency) {
            this.character = character;
            this.frequency = frequency;
        }

        Node(int frequency, Node left, Node right) {
            this.character = '\0';
            this.frequency = frequency;
            this.left = left;
            this.right = right;
        }
    }

    static class FrequencyComparator implements Comparator<Node>, Serializable {
        @Override
        public int compare(Node n1, Node n2) {
            if (n1.frequency != n2.frequency)
                return Integer.compare(n1.frequency, n2.frequency);
            return Character.compare(n1.character, n2.character);
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage:");
            System.out.println("To compress:   java HuffmanCoding compress <inputFile> <outputFile>");
            System.out.println("To decompress: java HuffmanCoding decompress <inputFile> <outputFile>");
            return;
        }

        String mode = args[0];
        String inputFile = args[1];
        String outputFile = args[2];

        try {
            if (mode.equalsIgnoreCase("compress")) {
                compress(inputFile, outputFile);
            } else if (mode.equalsIgnoreCase("decompress")) {
                decompress(inputFile, outputFile);
            } else {
                System.out.println("Invalid mode. Use 'compress' or 'decompress'.");
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void compress(String inputFile, String outputFile) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Paths.get(inputFile));

        if (fileBytes.length == 0) {
            System.out.println("EMPTY FILE");
            return;
        }

        String text = new String(fileBytes, "UTF-8");

        Map<Character, Integer> freqMap = new HashMap<>();
        for (char c : text.toCharArray()) {
            freqMap.put(c, freqMap.getOrDefault(c, 0) + 1);
        }

        if (freqMap.isEmpty()) {
            System.out.println("EMPTY FILE");
            return;
        }

        PriorityQueue<Node> pq = new PriorityQueue<>(new FrequencyComparator());
        for (var entry : freqMap.entrySet()) {
            pq.add(new Node(entry.getKey(), entry.getValue()));
        }

        Node root;
        if (pq.size() == 1) {
            Node single = pq.poll();
            root = new Node(single.frequency, single, null);
        } else {
            while (pq.size() > 1) {
                Node left = pq.poll();
                Node right = pq.poll();
                pq.add(new Node(left.frequency + right.frequency, left, right));
            }
            root = pq.poll();
        }

        Map<Character, String> codes = new HashMap<>();
        generateCodes(root, "", codes);

        if (codes.isEmpty() && freqMap.size() == 1) {
            char singleChar = freqMap.keySet().iterator().next();
            codes.put(singleChar, "0");
        }

        StringBuilder bitString = new StringBuilder();
        for (char c : text.toCharArray()) {
            bitString.append(codes.get(c));
        }

        byte[] compressedBytes = bitStringToBytes(bitString.toString());

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outputFile))) {
            oos.writeObject(freqMap);
            oos.writeInt(bitString.length());
            oos.write(compressedBytes);
        }

        printCompressionDetails(fileBytes.length, text, codes, root, inputFile, outputFile);
    }

    @SuppressWarnings("unchecked")
    private static void decompress(String inputFile, String outputFile) throws Exception {
        Map<Character, Integer> freqMap;
        int bitLength;
        byte[] compressedBytes;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(inputFile))) {
            freqMap = (Map<Character, Integer>) ois.readObject();
            bitLength = ois.readInt();
            compressedBytes = ois.readAllBytes();
        }

        PriorityQueue<Node> pq = new PriorityQueue<>(new FrequencyComparator());
        for (var entry : freqMap.entrySet()) {
            pq.add(new Node(entry.getKey(), entry.getValue()));
        }

        Node root;
        if (pq.size() == 1) {
            Node single = pq.poll();
            root = new Node(single.frequency, single, null);
        } else {
            while (pq.size() > 1) {
                Node left = pq.poll();
                Node right = pq.poll();
                pq.add(new Node(left.frequency + right.frequency, left, right));
            }
            root = pq.poll();
        }

        StringBuilder bitString = new StringBuilder();
        for (byte b : compressedBytes) {
            for (int i = 7; i >= 0; i--) {
                bitString.append(((b >> i) & 1) == 1 ? '1' : '0');
            }
        }
        bitString.setLength(bitLength);

        StringBuilder decodedText = new StringBuilder();
        Node current = root;

        if (root.left == null && root.right == null) {
            for (int i = 0; i < bitString.length(); i++) {
                decodedText.append(root.character);
            }
        } else {
            for (int i = 0; i < bitString.length(); i++) {
                char bit = bitString.charAt(i);
                current = (bit == '0') ? current.left : current.right;

                if (current.left == null && current.right == null) {
                    decodedText.append(current.character);
                    current = root;
                }
            }
        }

        byte[] outputBytes = decodedText.toString().getBytes("UTF-8");
        Files.write(Paths.get(outputFile), outputBytes);

        System.out.println("Decompression complete → " + outputFile);
    }

    private static byte[] bitStringToBytes(String bitString) {
        int len = (bitString.length() + 7) / 8;
        byte[] bytes = new byte[len];
        int byteIndex = 0, bitCount = 0, currentByte = 0;

        for (char bit : bitString.toCharArray()) {
            currentByte = (currentByte << 1) | (bit - '0');
            bitCount++;
            if (bitCount == 8) {
                bytes[byteIndex++] = (byte) currentByte;
                bitCount = 0;
                currentByte = 0;
            }
        }

        if (bitCount > 0) {
            currentByte <<= (8 - bitCount);
            bytes[byteIndex] = (byte) currentByte;
        }

        return bytes;
    }

    private static void generateCodes(Node node, String prefix, Map<Character, String> map) {
        if (node == null) return;
        if (node.character != '\0') {
            map.put(node.character, prefix.isEmpty() ? "0" : prefix);
        } else {
            generateCodes(node.left, prefix + "0", map);
            generateCodes(node.right, prefix + "1", map);
        }
    }

    private static void printCompressionDetails(int originalFileBytes, String originalText,
                                                Map<Character, String> codes, Node root,
                                                String inputFile, String outputFile) {
        int originalBits = originalFileBytes * 8;
        int encodedBits = 0;
        for (char c : originalText.toCharArray()) {
            encodedBits += codes.get(c).length();
        }

        double ratio = (1 - (double) encodedBits / originalBits) * 100;

        System.out.println("\nCompression Summary:");
        System.out.println("Input File: " + inputFile);
        System.out.println("Output File: " + outputFile);
        System.out.println("Original Size: " + originalBits + " bits (" + originalFileBytes + " bytes)");
        System.out.println("Compressed Size: " + encodedBits + " bits (" + ((encodedBits + 7) / 8) + " bytes)");
        System.out.printf("Compression Ratio: %.2f%%\n", ratio);
    }
}
