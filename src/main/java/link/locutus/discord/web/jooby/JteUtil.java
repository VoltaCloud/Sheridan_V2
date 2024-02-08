package link.locutus.discord.web.jooby;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jpson.PSON;
import com.jpson.PsonEncoder;
import com.jpson.PsonOptions;
import gg.jte.TemplateOutput;
import gg.jte.html.OwaspHtmlTemplateOutput;
import gg.jte.output.StringOutput;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.conflict.OffDefStatGroup;
import org.bson.BsonBinaryWriter;
import org.bson.BsonWriter;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class JteUtil {
//    public static void main(String[] args) {
//
//        Map<String, Object> root = new LinkedHashMap<>();
//        Map<String, Object> values = new LinkedHashMap<>();
//        values.put("testing", Map.of("one", 1, "two", 2, "three", 3));
//        ArrayList<Integer> myList = new ArrayList<>();
//        for (int i = 1; i < 65; i+=2) {
//            myList.add(i);
//        }
//        values.put("one", myList);
//
//        root.put("Test", List.of(values, values));
//
//        byte[] encoded = PSON.encode(root);
//        System.out.println("Encoded");
//
//        Map<String, Number> decoded = (Map<String, Number>) PSON.decode(encoded);
//        System.out.println("Decoded");
////
//        System.out.println(root);
//        System.out.println(decoded);
////
//        byte[] compressed = JteUtil.compress(encoded);
//        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
//        String key = Settings.INSTANCE.WEB.S3.ACCESS_KEY;
//        String secret = Settings.INSTANCE.WEB.S3.SECRET_ACCESS_KEY;
//        String region = Settings.INSTANCE.WEB.S3.REGION;
//        String bucket = Settings.INSTANCE.WEB.S3.BUCKET;
//        AwsManager aws = new AwsManager(key, secret, bucket, region);
//        aws.putObject("test.gzip", compressed);
//        System.out.println("done");
//    }

    public static String toB64(JsonObject json) {
        return Base64.getEncoder().encodeToString(json.toString().getBytes());
    }

    private static class CustomGZIPOutputStream extends GZIPOutputStream {
        CustomGZIPOutputStream(OutputStream out, int bytes) throws IOException {
            super(out, bytes);
            def.setLevel(Deflater.BEST_COMPRESSION);
        }
    }

    public static byte[] toBinary(Map<String, Object> map) {
        return PSON.encode(map);
    }
    public static byte[] compress(String b64String) {
        return compress(b64String.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] compress(byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gos = new CustomGZIPOutputStream(baos, 1048576)) {
                gos.write(data);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to gzip string", e);
        }
    }

    public static byte[] decompress(byte[] bytes) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            GZIPInputStream gis = new GZIPInputStream(bais);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            gis.close();
            baos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to decompress data", e);
        }
    }

    public static String render(Consumer<OwaspHtmlTemplateOutput> task) {
        TemplateOutput output = new StringOutput();
        OwaspHtmlTemplateOutput htmlOutput = new OwaspHtmlTemplateOutput(output);
        task.accept(htmlOutput);
        return output.toString();
    }

    public static <V, T> List<List<Object>> writeArray(List<List<Object>> parent, Collection<Function<T, Object>> functions, List<V> values, Map<V, Map.Entry<T, T>> map) {
        return writeArray(parent, functions, values, (aaId, consumer) -> {
            Map.Entry<T, T> pair = map.get(aaId);
            consumer.accept(pair == null ? null : pair.getKey());
            consumer.accept(pair == null ? null : pair.getValue());
        });
    }

    public static <V, T> List<List<Object>> writeArray(List<List<Object>> parent, Collection<Function<T, Object>> functions, Collection<V> collection, BiConsumer<V, Consumer<T>> provider) {
        List<T> obj = new ObjectArrayList<>();
        for (V value : collection) {
            provider.accept(value, obj::add);
        }
        return writeArray(parent, functions, obj);
    }

    public static <T> List<List<Object>> writeArray(List<List<Object>> parent, Collection<Function<T, Object>> functions, Collection<T> collection) {
        for (T value : collection) {
            List<Object> array = new ObjectArrayList<>();
            if (value == null) {
                for (Function<T, Object> function : functions) {
                    array.add("");
                }
            } else {
                for (Function<T, Object> function : functions) {
                    array.add(function.apply(value));
                }
            }
            parent.add(array);
        }
        return parent;
    }
//
//    public static JsonArray createArrayColNum(Collection<Number> myCollection) {
//        JsonArray array = new JsonArray();
//        for (Number value : myCollection) {
//            array.add(value);
//        }
//        return array;
//    }
//
//    public static JsonArray createArrayCol(Collection<String> myCollection) {
//        JsonArray array = new JsonArray();
//        for (String value : myCollection) {
//            array.add(value);
//        }
//        return array;
//    }
//
//    public static JsonArray createArrayColObj(Collection<Object> myCollection) {
//        JsonArray array = new JsonArray();
//        for (Object value : myCollection) {
//            add(array, value);
//        }
//        return array;
//    }
//
//
//    public static JsonArray createArrayObj(Object... values) {
//        JsonArray array = new JsonArray();
//        for (Object value : values) {
//            add(array, value);
//        }
//        return array;
//    }
//
//    public static JsonArray add(JsonArray array, Object value) {
//        if (value instanceof Number num) {
//            array.add(num);
//        } else if (value instanceof Boolean bool) {
//            array.add(bool);
//        } else if (value instanceof String str) {
//            array.add(str);
//        } else if (value instanceof JsonElement elem) {
//            array.add(elem);
//        } else {
//            array.add(value == null ? "" : value.toString());
//        }
//        return array;
//    }
}
