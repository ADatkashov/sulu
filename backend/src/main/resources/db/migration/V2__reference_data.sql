INSERT INTO service_categories (slug, name_kk, name_ru, name_en, name_tr, icon, sort_order) VALUES
 ('haircut',  'Шаш қиюы',        'Стрижки',        'Haircuts',     'Saç kesimi',   'scissors', 1),
 ('coloring', 'Шашты бояу',      'Окрашивание',    'Hair coloring','Saç boyama',   'palette',  2),
 ('manicure', 'Маникюр',         'Маникюр',        'Manicure',     'Manikür',      'hand',     3),
 ('pedicure', 'Педикюр',         'Педикюр',        'Pedicure',     'Pedikür',      'foot',     4),
 ('makeup',   'Макияж',          'Макияж',         'Makeup',       'Makyaj',       'brush',    5),
 ('brows',    'Қас',             'Брови',          'Eyebrows',     'Kaş',          'eye',      6),
 ('lashes',   'Кірпік',          'Ресницы',        'Eyelashes',    'Kirpik',       'eye',      7),
 ('skincare', 'Теріге күтім',    'Уход за кожей',  'Skincare',     'Cilt bakımı',  'droplet',  8),
 ('cosmetology','Косметология',  'Косметология',   'Cosmetology',  'Kozmetoloji',  'flask',    9),
 ('barber',   'Барбершоп',       'Барбершоп',      'Barbershop',   'Berber',       'razor',   10),
 ('massage',  'Массаж',          'Массаж',         'Massage',      'Masaj',        'leaf',    11),
 ('spa',      'СПА',             'СПА',            'Spa',          'Spa',          'lotus',   12);

INSERT INTO subscription_plans (code, name, price_minor, currency, features) VALUES
 ('FREE',   'Free',   0, 'KZT', 'Базовый профиль, онлайн-запись'),
 ('PRO',    'Pro',    0, 'KZT', 'Расширенная аналитика, промокоды (цена не установлена)'),
 ('PREMIUM','Premium',0, 'KZT', 'Продвижение в выдаче (цена не установлена)');

INSERT INTO platform_settings (key, value) VALUES
 ('commission_bps', '1000'),
 ('default_city', 'Алматы'),
 ('default_currency', 'KZT'),
 ('default_timezone', 'Asia/Almaty');
