# Keep stack traces readable for this personal sideload build while retaining
# R8 shrinking, optimization, and resource shrinking.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# Jsoup can optionally use RE2/J when that optional library is present. DIMA Now
# uses Jsoup's default java.util.regex implementation and does not bundle RE2/J.
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
