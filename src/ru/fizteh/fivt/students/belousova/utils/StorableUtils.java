package ru.fizteh.fivt.students.belousova.utils;

import ru.fizteh.fivt.storage.structured.ColumnFormatException;
import ru.fizteh.fivt.storage.structured.Storeable;
import ru.fizteh.fivt.storage.structured.Table;
import ru.fizteh.fivt.storage.structured.TableProvider;
import ru.fizteh.fivt.students.belousova.storable.StorableTableLine;

import javax.xml.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StorableUtils {
    public static void readSignature(File file, List<Class<?>> columnTypes) throws IOException {
        if (!file.exists()) {
            throw new IOException("signature.tsv doesn't exist");
        }
        if (file.length() == 0) {
            throw new IOException("signature.tsv is empty");
        }

        try {
            InputStream is = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(is, 4096);
            DataInputStream dis = new DataInputStream(bis);
            try {
                int position = 0;
                while (position != file.length()) {
                    String type = readType(dis);
                    position += type.getBytes(StandardCharsets.UTF_8).length + 1;
                    Class<?> classType = convertStringToClass(type);
                    columnTypes.add(classType);
                }
            } finally {
                FileUtils.closeStream(dis);
            }
        } catch (IOException e) {
            throw new IOException("read error", e);
        }
    }

    public static Class<?> convertStringToClass(String s) throws IOException {
        switch (s) {
            case "int":
                return Integer.class;
            case "long":
                return Long.class;
            case "byte":
                return Byte.class;
            case "float":
                return Float.class;
            case "double":
                return Double.class;
            case "boolean":
                return Boolean.class;
            case "String":
                return String.class;
            default:
                throw new IOException("type has wrong format");
        }
    }

    private static String readType(DataInputStream dis) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte b = dis.readByte();
        int length = 0;
        while (b != ' ') {
            bos.write(b);
            b = dis.readByte();
            length++;
            if (length > 1024 * 1024) {
                throw new IOException("signature.tsv has wrong format");
            }
        }
        if (length == 0) {
            throw new IOException("signature.tsv has wrong format");
        }
        return bos.toString(StandardCharsets.UTF_8.toString());
    }

    public static void readTable(File file, Table table, Map<String, Storeable> dataBase,
                                 TableProvider tableProvider) throws IOException {
        Map<String, String> stringMap = new HashMap<>();
        MultiFileUtils.read(file, stringMap);

        for (String key : stringMap.keySet()) {
            try {
                Storeable value = tableProvider.deserialize(table, stringMap.get(key));
                dataBase.put(key, value);
            } catch (ParseException e) {
                throw new IOException("read error", e);
            }
        }
    }

    public static void writeSignature(File directory, List<Class<?>> columnTypes) throws IOException {
        File signatureFile = new File(directory, "signature.tsv");
        signatureFile.createNewFile();
        OutputStream os = new FileOutputStream(signatureFile);
        BufferedOutputStream bos = new BufferedOutputStream(os, 4096);
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            for (Class<?> type : columnTypes) {
                String typeString = convertClassToString(type);
                dos.write(typeString.getBytes(StandardCharsets.UTF_8));
                dos.write(' ');
            }
        } finally {
            FileUtils.closeStream(dos);
        }
    }

    private static String convertClassToString(Class<?> type) throws IOException {
        switch (type.getName()) {
            case "java.lang.Integer":
                return "int";
            case "java.lang.Long":
                return "long";
            case "java.lang.Byte":
                return "byte";
            case "java.lang.Float":
                return "float";
            case "java.lang.Double":
                return "double";
            case "java.lang.Boolean":
                return "boolean";
            case "java.lang.String":
                return "String";
            default:
                throw new IOException("write error");
        }
    }

    public static Storeable readStorableValue(String s, List<Class<?>> columnTypes) throws ParseException {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        Storeable line = new StorableTableLine(columnTypes);
        try {
            XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(s));
            try {
                if (!reader.hasNext()) {
                    throw new ParseException("input string is empty", 0);
                }
                reader.nextTag();
                if (!reader.getLocalName().equals("row")) {
                    throw new ParseException("invalid xml format", reader.getLocation().getCharacterOffset());
                }
                int columnIndex = 0;
                while (reader.hasNext()) {
                    reader.nextTag();
                    if (!reader.getLocalName().equals("col")) {
                        if (!reader.isEndElement()) {
                            throw new ParseException("invalid xml format", reader.getLocation().getCharacterOffset());
                        }
                        if (reader.isEndElement() && reader.getLocalName().equals("row")) {
                            break;
                        }
                    }
                    String text = reader.getElementText();
                    if (!text.equals("null")) {
                        line.setColumnAt(columnIndex, parseValue(text, columnTypes.get(columnIndex)));
                    }
                    columnIndex++;
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new ParseException("xml reading error", 0);
        } catch (ColumnFormatException e) {
            throw new ParseException("xml reading error", 0);
        }
        return line;
    }

    private static Object parseValue(String s, Class<?> classType) {
        try {
            switch (classType.getName()) {
                case "java.lang.Integer":
                    return Integer.parseInt(s);
                case "java.lang.Long":
                    return Long.parseLong(s);
                case "java.lang.Byte":
                    return Byte.parseByte(s);
                case "java.lang.Float":
                    return Float.parseFloat(s);
                case "java.lang.Double":
                    return Double.parseDouble(s);
                case "java.lang.Boolean":
                    return Boolean.parseBoolean(s);
                case "java.lang.String":
                    return s;
                default:
                    throw new ColumnFormatException("wrong column format");
            }
        } catch (NumberFormatException e) {
            throw new ColumnFormatException("column format error");
        }
    }

    public static String writeStorableToString(Storeable storeable, List<Class<?>> columnTypes) {
        XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        StringWriter stringWriter = new StringWriter();
        try {
            XMLStreamWriter writer = outputFactory.createXMLStreamWriter(stringWriter);
            try {
                writer.writeStartElement("row");
                for (int i = 0; i < columnTypes.size(); i++) {
                    writer.writeStartElement("col");
                    if (storeable.getColumnAt(i) == null) {
                        writer.writeCharacters("null");
                    } else {
                        writer.writeCharacters(getStringFromElement(storeable, i, columnTypes.get(i)));
                    }
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            } finally {
                writer.close();
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        } finally {
            FileUtils.closeStream(stringWriter);
        }
        return stringWriter.toString();
    }

    private static String getStringFromElement(Storeable storeable, int columnIndex, Class<?> columnType) {
        switch (columnType.getName()) {
            case "java.lang.Integer":
                return Integer.toString(storeable.getIntAt(columnIndex));
            case "java.lang.Long":
                return Long.toString(storeable.getLongAt(columnIndex));
            case "java.lang.Byte":
                return Byte.toString(storeable.getByteAt(columnIndex));
            case "java.lang.Float":
                return Float.toString(storeable.getFloatAt(columnIndex));
            case "java.lang.Double":
                return Double.toString(storeable.getDoubleAt(columnIndex));
            case "java.lang.Boolean":
                return Boolean.toString(storeable.getBooleanAt(columnIndex));
            case "java.lang.String":
                return storeable.getStringAt(columnIndex);
            default:
                throw new ColumnFormatException("wrong column format");
        }
    }

    public static void writeTable(File file, Table table, Map<String, Storeable> storeableMap,
                                  TableProvider tableProvider) throws IOException {
        Map<String, String> stringMap = new HashMap<>();
        for (String key : storeableMap.keySet()) {
            stringMap.put(key, tableProvider.serialize(table, storeableMap.get(key)));
        }
        MultiFileUtils.write(file, stringMap);
    }
}