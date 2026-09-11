<?php
// kp_final_update_v7.php
ini_set('display_errors', 1);
error_reporting(E_ALL);
set_time_limit(0);
mb_internal_encoding('UTF-8');

$dbHost = '127.0.0.1';
$dbUser = 'uztv_user3';
$dbPass = 'StrongPass2025!';
$dbName = 'uztv_su';
$kp_api_key = 'd02ffd4f-0440-4426-9e07-9ee33c306f6d';
$logoBaseDir = '/var/www/uztv.su/uploads/logos/';
$logoBaseUrl = '/uploads/logos/';

$mounts = [
    '/var/www/uztv.su/Mediateka-hdd1/',
    '/var/www/uztv.su/Mediateka-hdd2/',
    '/var/www/uztv.su/Mediateka-hdd3/',
    '/var/www/uztv.su/Mediateka_Kolobok/',
    '/mnt/ssd_usb/Mediateka-hdd1_SSD/',
    '/mnt/hdd4_12tb/Mediateka_Kolobok/'
];

// --- Подключение к БД ---
$mysqli = new mysqli($dbHost, $dbUser, $dbPass, $dbName);
if ($mysqli->connect_errno) die("❌ Ошибка подключения к БД: " . $mysqli->connect_error);
$mysqli->set_charset("utf8");

// --- Вспомогательные функции ---
function kp_get($url, $key) {
    $ch = curl_init($url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_HTTPHEADER, ["X-API-KEY: $key", "Content-Type: application/json"]);
    curl_setopt($ch, CURLOPT_TIMEOUT, 25);
    $res = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    return $code == 200 ? json_decode($res, true) : null;
}

function cleanTitle($file) {
    $n = strtolower($file);
    $n = preg_replace('/\.(mp4|mkv|avi|ts|m2ts)$/i', '', $n);
    $n = preg_replace('/\b(webrip|hdrip|1080p|720p|2160p|4k|bdrip|dvdrip|webdl|hdtv|x264|x265|rip|ma|dub|money|studio)\b/i', '', $n);
    $n = preg_replace('/[^a-zA-Z0-9а-яА-ЯёЁ\s]/u', ' ', $n);
    $n = preg_replace('/\s+/', ' ', $n);
    return trim($n);
}

function makeLogoDir($base, $id) {
    $dir = rtrim($base, '/') . "/$id/";
    if (!is_dir($dir)) mkdir($dir, 0777, true);
    return $dir;
}

function downloadImage($url, $save) {
    $img = @file_get_contents($url);
    if ($img) { file_put_contents($save, $img); return true; }
    return false;
}

function findFile($filename, $mounts) {
    foreach ($mounts as $mount) {
        if (!is_dir($mount)) continue;
        $iterator = new RecursiveIteratorIterator(new RecursiveDirectoryIterator(rtrim($mount, '/')));
        foreach ($iterator as $file) {
            if ($file->isFile() && strtolower($file->getFilename()) == strtolower($filename)) {
                return $file->getPathname();
            }
        }
    }
    return null;
}

function updateMovieFull($mysqli, $kp_api_key, $logoBaseDir, $logoBaseUrl, $mounts, $movieId, $skipFilled = true) {
    echo "\n===========================\n";
    echo "🔍 Обработка LOCAL ID=$movieId\n";

    $res = $mysqli->query("SELECT * FROM videos WHERE ID=$movieId LIMIT 1");
    $video = $res->fetch_assoc();
    if (!$video) { echo "❌ Видео не найдено в БД.\n"; return false; }

    // 🔥 НОВАЯ ЛОГИКА ПРОПУСКА 🔥
    // Если флаг skipFilled включен (по папке и массово), проверяем заполненность
    if ($skipFilled) {
        if (!empty($video['RUNAME']) && !empty($video['RUDESCRIPTION']) && !empty($video['LOGO'])) {
            echo "⏭️ ПРОПУСК: Фильм '{$video['RUNAME']}' уже готов (есть логотип и описание).\n";
            return false; // Возвращаем false, чтобы не засчитывать как "новое обновление"
        }
    }

    $originalName = $video['FILE'] ?? '';
    $filePath = findFile($originalName, $mounts);

    if ($filePath) {
        echo "Найден файл: $filePath\n";
    }
    echo "Название из файла: '$originalName'\n";

    $searchTitle = cleanTitle($originalName);
    preg_match('/\b(19|20)\d{2}\b/', $originalName, $m);
    $searchYear = $m[0] ?? '';

    $searchTitleForLog = str_replace('.', ' ', $searchTitle);
    echo "Название для поиска: '$searchTitleForLog' (год: $searchYear)\n";

    // --- Поиск на Кинопоиске ---
    $url = "https://kinopoiskapiunofficial.tech/api/v2.1/films/search-by-keyword?keyword=" . urlencode($searchTitleForLog);
    $search = kp_get($url, $kp_api_key);
    
    // 🔥 Пауза теперь здесь, она сработает только если мы реально обратились к API
    sleep(1); 

    // Если не найдено, сокращаем слова по одному
    if (empty($search['films'])) {
        $parts = explode(' ', $searchTitleForLog);
        while (count($parts) > 1) {
            array_pop($parts);
            $try = implode(' ', $parts);
            $search = kp_get("https://kinopoiskapiunofficial.tech/api/v2.1/films/search-by-keyword?keyword=" . urlencode($try), $kp_api_key);
            sleep(1);
            if (!empty($search['films'])) { $searchTitleForLog = $try; break; }
        }
    }

    if (empty($search['films'][0]['filmId'])) { echo "❌ Не найдено на Кинопоиске.\n"; return false; }

    $filmData = $search['films'][0];
    $kpId = $filmData['filmId'];
    $runame = $filmData['nameRu'] ?? $filmData['nameEn'] ?? $searchTitleForLog;

    echo "✅ Найден фильм: $runame (KP ID: $kpId)\n";

    // --- Детали фильма ---
    $url = "https://kinopoiskapiunofficial.tech/api/v2.2/films/$kpId";
    $film = kp_get($url, $kp_api_key);
    sleep(1);
    
    if (!$film) { echo "❌ Ошибка загрузки деталей фильма.\n"; return false; }

    $desc = $film['description'] ?? 'Описание отсутствует';
    $year = $film['year'] ?? $searchYear;
    $poster = $film['posterUrlPreview'] ?? $film['posterUrl'] ?? '';
    $genres = array_column($film['genres'] ?? [], 'genre');
    $countryArr = [];
    foreach ($film['countries'] ?? [] as $c) if (!empty($c['country'])) $countryArr[] = $c['country'];
    $country = implode(', ', $countryArr);

    $age = '+0';
    if (!empty($film['ratingAgeLimits'])) {
        preg_match('/\d+/', $film['ratingAgeLimits'], $m);
        if (isset($m[0])) $age = '+' . $m[0];
    }

    $directorArr = []; $actorsArr = [];
    foreach ($film['persons'] ?? [] as $p) {
        $prof = $p['professionKey'] ?? '';
        $name = $p['nameRu'] ?? $p['nameEn'] ?? '';
        if (!$name) continue;
        if ($prof === 'DIRECTOR') $directorArr[] = $name;
        if ($prof === 'ACTOR') $actorsArr[] = $name;
    }

    if (empty($directorArr) || empty($actorsArr)) {
        $url = "https://kinopoiskapiunofficial.tech/api/v1/staff?filmId=$kpId";
        $staff = kp_get($url, $kp_api_key);
        if ($staff) {
            foreach ($staff as $p) {
                $name = $p['nameRu'] ?? $p['nameEn'] ?? '';
                if (!$name) continue;
                if ($p['professionKey'] == 'DIRECTOR' && empty($directorArr)) $directorArr[] = $name;
                if ($p['professionKey'] == 'ACTOR' && count($actorsArr) < 10) $actorsArr[] = $name;
            }
        }
    }

    $director = implode(', ', $directorArr);
    $actors = implode(', ', array_slice($actorsArr, 0, 10));

    // --- Постер ---
    $logoDir = makeLogoDir($logoBaseDir, $movieId);
    $posterFile = $logoDir . "$movieId.jpg";
    $posterPath = '';
    if ($poster && downloadImage($poster, $posterFile)) {
        echo "📸 Постер сохранён: $posterFile\n";
        $posterPath = $logoBaseUrl . "$movieId/$movieId.jpg";
    }

    // --- Обновление БД ---
    $runameWithYear = $runame . " ($year)";
    $sql = "UPDATE videos SET RUNAME=?, RUDESCRIPTION=?, MAKEYEAR=?, LOGO=?, COUNTRY=?, AGE=?, MAKER=?, ACTORS=? WHERE ID=?";
    $stmt = $mysqli->prepare($sql);
    $stmt->bind_param('ssissssss', $runameWithYear, $desc, $year, $posterPath, $country, $age, $director, $actors, $movieId);
    $stmt->execute();
    $stmt->close();

    // --- Жанры ---
    foreach ($genres as $g) {
        $gname = trim($g);
        if ($gname == '') continue;
        $res = $mysqli->query("SELECT ID FROM video_rubrikas WHERE NAME='" . $mysqli->real_escape_string($gname) . "' LIMIT 1");
        if ($res && $row = $res->fetch_assoc()) $rubId = $row['ID'];
        else {
            $mysqli->query("INSERT INTO video_rubrikas(NAME,ACTIVE) VALUES('" . $mysqli->real_escape_string($gname) . "',1)");
            $rubId = $mysqli->insert_id;
        }
        $chk = $mysqli->query("SELECT 1 FROM videos_in_rubrikas WHERE VIDEO_ID=$movieId AND RUB_ID=$rubId LIMIT 1");
        if (!($chk && $chk->fetch_assoc())) {
            $mysqli->query("INSERT INTO videos_in_rubrikas(VIDEO_ID,RUB_ID) VALUES($movieId,$rubId)");
            echo "✅ Жанр '$gname' добавлен к фильму ID=$movieId\n";
        }
    }

    echo "===========================\n";
    echo "🎬 Обработка: $runameWithYear (ORIG: $originalName) (ID: $movieId)\n";
    echo "🎭 Жанры: " . implode(', ', $genres) . "\n";
    echo "===========================\n";
    
    return true; // Фильм успешно спарсился и обновился
}

// --- Обработка аргументов командной строки ---
$options = getopt("", ["id::", "papka::"]);
$id = $options['id'] ?? null;
$papka = $options['papka'] ?? null;

if (!$id && !$papka) {
    echo "❌ Ошибка! Укажите один из параметров:\n";
    echo "   1. По папке: php kp_final_update_v7.php --papka=/mnt/hdd4_12tb/Mediateka_Kolobok/VOD-02\n";
    echo "   2. По ID:    php kp_final_update_v7.php --id=24400\n";
    echo "   3. Все сразу: php kp_final_update_v7.php --id=all\n";
    exit;
}

// --- РЕЖИМ 1: Обновление по папке ---
if ($papka) {
    $folderPath = rtrim($papka, '/');
    if (!is_dir($folderPath)) {
        die("❌ Указанная папка не существует: $folderPath\n");
    }

    echo "📂 Сканирование папки: $folderPath\n";

    $iterator = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($folderPath));
    $processedCount = 0;
    $skippedCount = 0;

    foreach ($iterator as $file) {
        if ($file->isFile() && preg_match('/\.(mp4|mkv|avi|ts|m2ts)$/i', $file->getFilename())) {
            $filename = $file->getFilename();
            $escapedFilename = $mysqli->real_escape_string($filename);

            $res = $mysqli->query("SELECT ID FROM videos WHERE FILE = '$escapedFilename' LIMIT 1");
            if ($res && $row = $res->fetch_assoc()) {
                // Пытаемся обновить (true - пропускать заполненные)
                if (updateMovieFull($mysqli, $kp_api_key, $logoBaseDir, $logoBaseUrl, $mounts, $row['ID'], true)) {
                    $processedCount++;
                } else {
                    $skippedCount++;
                }
            } else {
                echo "\n⚠️ Файл '$filename' не найден в базе данных. Пропущен.\n";
            }
        }
    }
    echo "\n🎉 ОБРАБОТКА ЗАВЕРШЕНА! Обновлено новых: $processedCount | Пропущено готовых: $skippedCount\n";
} 
// --- РЕЖИМ 2: Обновление всех видео ---
else if ($id == 'all') {
    $res = $mysqli->query("SELECT ID FROM videos");
    $processedCount = 0;
    $skippedCount = 0;
    while ($row = $res->fetch_assoc()) {
        if (updateMovieFull($mysqli, $kp_api_key, $logoBaseDir, $logoBaseUrl, $mounts, $row['ID'], true)) {
            $processedCount++;
        } else {
            $skippedCount++;
        }
    }
    echo "\n🎉 ОБРАБОТКА ЗАВЕРШЕНА! Обновлено: $processedCount | Пропущено готовых: $skippedCount\n";
} 
// --- РЕЖИМ 3: Обновление по конкретному ID ---
else {
    // При вызове по ID ставим флаг false, чтобы он обновил фильм принудительно, даже если тот уже заполнен
    updateMovieFull($mysqli, $kp_api_key, $logoBaseDir, $logoBaseUrl, $mounts, intval($id), false);
}

$mysqli->close();