# Diari Kaunselor — Versi Desktop (Windows PWA)

Versi Desktop ini bersambung terus ke pangkalan data **Cloud Firestore** dan sistem penyulitan hujung-ke-hujung (**E2EE AES-256-GCM**) yang sama dengan aplikasi Android **Diari Kaunselor**.

## Cara 1: Buka Secara Dalam Talian (GitHub Pages — Disyorkan)
1. Tolak (*push*) repositori ini ke GitHub dan aktifkan **Settings → Pages → Source: GitHub Actions**.
2. Aliran kerja **Terbit Diari Kaunselor Desktop (PWA)** akan menerbitkan laman web di `https://<nama-pengguna>.github.io/<nama-repo>/`.
3. **Langkah Sekali Sahaja (Google Sign-In):** Buka **Firebase Console → Authentication → Settings → Authorized domains** dan tambah `<nama-pengguna>.github.io`.
4. Buka URL tersebut dalam **Google Chrome** atau **Microsoft Edge** di Windows, kemudian klik ikon **Pasang (Install)** pada bar alamat untuk memasangnya di Desktop & Taskbar Windows.

## Cara 2: Jalankan Terus pada Windows (Tempatan — Tanpa Konfigurasi Domain)
1. Pastikan [Node.js LTS](https://nodejs.org/) telah dipasang pada Windows.
2. Klik dua kali fail `start-desktop.bat` di dalam folder `desktop-web/`.
3. Pelayar akan membuka `http://localhost:4173` (`localhost` sudah dibenarkan secara automatik oleh Firebase Authentication).
4. Log masuk menggunakan **Akaun Google yang sama** seperti di aplikasi Android.
