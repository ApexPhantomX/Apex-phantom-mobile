package com.apexphantom.mobile;

interface IShellService {
    // Shizuku'nun UserService'i kapatmak için kullandığı ayrılmış işlem kodu.
    void destroy() = 16777114;

    // Komutu Shizuku (ADB) yetkisiyle çalıştırır, çıktıyı döndürür.
    String exec(String command) = 1;
}
