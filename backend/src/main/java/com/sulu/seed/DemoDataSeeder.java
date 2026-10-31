package com.sulu.seed;

import com.sulu.common.SuluProperties;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT demo data (SULU_SEED_DEMO=true). Everything here is FICTIONAL and stored with businesses.is_demo=true so the UI
 * labels it "DEMO". Runs only when no demo business exists yet, so it is safe to restart. Never enable in production.
 */
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String PASSWORD = "Demo12345!";
    private static final ZoneId ALMATY = ZoneId.of("Asia/Almaty");

    private final JdbcClient db; private final SuluProperties props; private final PasswordEncoder encoder; private final Clock clock;
    public DemoDataSeeder(JdbcClient db, SuluProperties props, PasswordEncoder encoder, Clock clock) { this.db = db; this.props = props; this.encoder = encoder; this.clock = clock; }

    // category, name, price (major units), minutes, tags, description
    record Svc(String cat, String name, long price, int min, String tags, String desc, boolean from) {}
    record Pro(String name, String title, String specialties, String gender, int exp, String bio, int[] svc) {}
    record Biz(String name, String kind, String city, String address, double lat, double lng, String phone, String desc, int cancelH, boolean auto, Svc[] services, Pro[] pros, boolean sunday) {}

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seedDemo()) return;
        if (db.sql("SELECT count(*) FROM businesses WHERE is_demo").query(Long.class).single() > 0) return;
        String hash = encoder.encode(PASSWORD);
        long owner = user("owner@demo.sulu", "Айгерим Demo (владелец)", "BUSINESS_OWNER", hash);
        long anna = user("anna@demo.sulu", "Анна Демо", "CUSTOMER", hash);
        long[] reviewers = {user("maria@demo.sulu", "Мария Тестова", "CUSTOMER", hash), user("ekaterina@demo.sulu", "Екатерина Образцова", "CUSTOMER", hash),
                user("dana@demo.sulu", "Дана Примерова", "CUSTOMER", hash), user("alua@demo.sulu", "Алуа Демо", "CUSTOMER", hash), user("john@demo.sulu", "John Sample", "CUSTOMER", hash)};
        long master = user("master@demo.sulu", "Мастер Демо", "PROFESSIONAL", hash);

        Biz[] bizs = {
            new Biz("Aru Beauty Studio", "SALON", "Алматы", "ул. Демо-Достык, 100", 43.2380, 76.9570, "+7 700 000 00 01",
                "ДЕМО-салон: стрижки, окрашивание и уход за волосами. Вымышленный бизнес для разработки.", 24, true,
                new Svc[]{ new Svc("haircut", "Женская стрижка", 8000, 60, "стрижка,волосы,haircut", "Консультация, мытьё, стрижка и укладка.", false),
                           new Svc("haircut", "Стрижка кончиков", 4500, 30, "стрижка,кончики", "Освежение формы без изменения длины.", false),
                           new Svc("coloring", "Окрашивание волос", 22000, 150, "окрашивание,цвет,coloring", "Индивидуальный подбор оттенка, полный цикл.", true),
                           new Svc("coloring", "Airtouch", 38000, 240, "airtouch,блонд", "Сложное окрашивание с плавным переходом.", true),
                           new Svc("skincare", "Уход для лица", 12000, 60, "лицо,уход,skincare", "Очищение, маска, массаж лица.", false)},
                new Pro[]{ new Pro("Анастасия Демо", "Стилист-колорист", "стрижки, окрашивание, airtouch", "FEMALE", 7, "Опыт более 7 лет. Подбираю индивидуальный образ и идеальный оттенок.", new int[]{0,1,2,3}),
                           new Pro("Мадина Образец", "Топ-стилист", "женские стрижки, укладки", "FEMALE", 10, "Стрижки любой сложности, работаю с длинными волосами.", new int[]{0,1}),
                           new Pro("Ляззат Пример", "Косметолог", "уход за лицом, чистка", "FEMALE", 5, "Подбор ухода по типу кожи.", new int[]{4})}, false),
            new Biz("Lotus Nail Bar", "STUDIO", "Алматы", "пр. Демо-Абая, 45", 43.2430, 76.9300, "+7 700 000 00 02",
                "ДЕМО-студия маникюра и педикюра. Вымышленный бизнес.", 12, true,
                new Svc[]{ new Svc("manicure", "Маникюр с покрытием", 6500, 90, "маникюр,гель-лак,manicure", "Обработка, покрытие гель-лаком, дизайн по желанию.", false),
                           new Svc("manicure", "Классический маникюр", 4000, 60, "маникюр,классический", "Аппаратная и классическая обработка.", false),
                           new Svc("pedicure", "Педикюр", 8000, 90, "педикюр,pedicure", "Комплексный уход за стопами.", false),
                           new Svc("manicure", "Наращивание ногтей", 12000, 150, "наращивание,ногти", "Моделирование формы, покрытие.", true)},
                new Pro[]{ new Pro("Алина Пример", "Мастер маникюра", "маникюр, дизайн", "FEMALE", 6, "Аккуратный маникюр и сложные дизайны.", new int[]{0,1,3}),
                           new Pro("Сабина Демо", "Мастер педикюра", "педикюр, маникюр", "FEMALE", 4, "Безопасная стерильная обработка.", new int[]{1,2})}, false),
            new Biz("Qara Barbershop", "BARBERSHOP", "Алматы", "ул. Демо-Тулебаева, 12", 43.2510, 76.9460, "+7 700 000 00 03",
                "ДЕМО-барбершоп: мужские стрижки и уход за бородой. Вымышленный бизнес.", 6, true,
                new Svc[]{ new Svc("barber", "Мужская стрижка", 7000, 45, "стрижка,мужская,barber", "Стрижка машинкой и ножницами, укладка.", false),
                           new Svc("barber", "Оформление бороды", 4000, 30, "борода,beard", "Моделирование и уход за бородой.", false),
                           new Svc("barber", "Стрижка + борода", 10000, 75, "комбо,стрижка,борода", "Комплекс для полного образа.", false)},
                new Pro[]{ new Pro("Тимур Пример", "Барбер", "мужские стрижки, борода", "MALE", 8, "Классические и современные мужские стрижки.", new int[]{0,1,2}),
                           new Pro("Ерлан Демо", "Барбер", "fade, борода", "MALE", 5, "Fade и чёткие контуры.", new int[]{0,2})}, true),
            new Biz("Nur Brow & Lash", "STUDIO", "Астана", "ул. Демо-Мангилик Ел, 20", 51.0900, 71.4180, "+7 700 000 00 04",
                "ДЕМО-студия бровей, ресниц и макияжа. Вымышленный бизнес.", 24, false,
                new Svc[]{ new Svc("brows", "Коррекция и окрашивание бровей", 5000, 45, "брови,коррекция,brows", "Форма, окрашивание краской или хной.", false),
                           new Svc("lashes", "Наращивание ресниц", 11000, 120, "ресницы,наращивание,lashes", "Классика и 2D объём.", true),
                           new Svc("makeup", "Вечерний макияж", 12000, 75, "макияж,вечерний,makeup", "Макияж для мероприятия с подбором образа.", false),
                           new Svc("makeup", "Свадебный макияж", 25000, 120, "свадьба,макияж", "Пробный образ и стойкий макияж в день события.", true)},
                new Pro[]{ new Pro("Камила Пример", "Бровист-лэшмейкер", "брови, ресницы", "FEMALE", 6, "Естественные брови и ресницы под ваш образ.", new int[]{0,1}),
                           new Pro("Диляра Образец", "Визажист", "макияж, свадебный образ", "FEMALE", 9, "Макияж для любых событий.", new int[]{2,3})}, false),
            new Biz("Serene Spa & Massage", "STUDIO", "Алматы", "ул. Демо-Фурманова, 77", 43.2500, 76.9550, "+7 700 000 00 05",
                "ДЕМО-спа: массаж и релакс. Вымышленный бизнес.", 24, true,
                new Svc[]{ new Svc("massage", "Классический массаж спины", 9000, 60, "массаж,спина,massage", "Расслабляющий массаж спины и шеи.", false),
                           new Svc("massage", "Расслабляющий массаж всего тела", 16000, 90, "массаж,релакс", "Полное расслабление.", false),
                           new Svc("spa", "СПА-программа «Релакс»", 28000, 150, "спа,spa,релакс", "Пилинг, обёртывание, массаж.", true)},
                new Pro[]{ new Pro("Асель Демо", "Массажист", "массаж, релакс", "FEMALE", 11, "Работаю в технике классического и релакс-массажа.", new int[]{0,1,2}),
                           new Pro("Нурлан Пример", "Массажист", "спортивный массаж", "MALE", 7, "Помогаю снять напряжение после тренировок.", new int[]{0,1})}, true)
        };

        Map<String, Long> cats = new HashMap<>();
        db.sql("SELECT id, slug FROM service_categories").query().listOfRows().forEach(r -> cats.put((String) r.get("slug"), ((Number) r.get("id")).longValue()));

        List<long[]> allPros = new ArrayList<>(); // {proId, serviceId, businessId}
        String[] reviewTexts = {"Очень довольна результатом, мастер внимательный!", "Отличный сервис, всё чисто и уютно. Приду ещё.", "Хорошо, но немного ждала начала.",
                "Great service, highly recommend (demo review).", "Сделали именно то, что просила. Спасибо!", "Профессионально и быстро.", "Всё понравилось, рекомендую подругам."};
        int[] ratings = {5, 5, 4, 5, 5, 4, 5, 3, 5, 4};
        Random rnd = new Random(42);
        boolean first = true;
        for (Biz b : bizs) {
            long bid = db.sql("""
                INSERT INTO businesses(owner_id, name, kind, description, city, address, lat, lng, phone, timezone, currency, status, is_demo, auto_confirm, cancellation_hours)
                VALUES (:o,:n,:k,:d,:c,:a,:la,:ln,:p,:tz,'KZT','APPROVED',TRUE,:ac,:ch) RETURNING id""")
                .param("o", owner).param("n", b.name()).param("k", b.kind()).param("d", b.desc()).param("c", b.city()).param("a", b.address()).param("la", b.lat()).param("ln", b.lng())
                .param("p", b.phone()).param("tz", b.city().equals("Астана") ? "Asia/Almaty" : "Asia/Almaty").param("ac", b.auto()).param("ch", b.cancelH()).query(Long.class).single();
            db.sql("INSERT INTO business_subscriptions(business_id, plan_id) SELECT :b, id FROM subscription_plans WHERE code='FREE'").param("b", bid).update();
            for (int d = 1; d <= (b.sunday() ? 7 : 6); d++)
                db.sql("INSERT INTO business_hours VALUES (:b,:w,:o,:c)").param("b", bid).param("w", d).param("o", java.sql.Time.valueOf(LocalTime.of(10, 0))).param("c", java.sql.Time.valueOf(LocalTime.of(b.kind().equals("BARBERSHOP") ? 21 : 20, 0))).update();
            long[] svcIds = new long[b.services().length];
            for (int i = 0; i < svcIds.length; i++) {
                Svc s = b.services()[i];
                svcIds[i] = db.sql("INSERT INTO services(business_id, category_id, name, description, price_minor, price_from, currency, duration_min, tags) VALUES (:b,:c,:n,:d,:p,:pf,'KZT',:m,:t) RETURNING id")
                        .param("b", bid).param("c", cats.get(s.cat())).param("n", s.name()).param("d", s.desc()).param("p", s.price() * 100).param("pf", s.from()).param("m", s.min()).param("t", s.tags()).query(Long.class).single();
            }
            for (Pro p : b.pros()) {
                Long userId = first ? master : null;
                long pid = db.sql("INSERT INTO professionals(business_id, user_id, full_name, title, bio, experience_years, specialties, gender) VALUES (:b,:u,:n,:t,:bio,:e,:s,:g) RETURNING id")
                        .param("b", bid).param("u", userId).param("n", p.name()).param("t", p.title()).param("bio", p.bio()).param("e", p.exp()).param("s", p.specialties()).param("g", p.gender()).query(Long.class).single();
                first = false;
                for (int si : p.svc()) db.sql("INSERT INTO professional_services VALUES (:p,:s)").param("p", pid).param("s", svcIds[si]).update();
                for (int d = 1; d <= (b.sunday() ? 7 : 6); d++)
                    db.sql("INSERT INTO professional_working_hours VALUES (:p,:w,:s,:e,:bs,:be)").param("p", pid).param("w", d).param("s", java.sql.Time.valueOf(LocalTime.of(10, 0)))
                      .param("e", java.sql.Time.valueOf(LocalTime.of(b.kind().equals("BARBERSHOP") ? 21 : 19, 0))).param("bs", java.sql.Time.valueOf(LocalTime.of(13, 0))).param("be", java.sql.Time.valueOf(LocalTime.of(14, 0))).update();
                allPros.add(new long[]{pid, svcIds[p.svc()[0]], bid});
            }
        }

        // history: completed bookings + reviews, so ratings are computed from real review rows
        LocalDate today = LocalDate.now(clock.withZone(ALMATY));
        int idx = 0;
        for (long[] pr : allPros) {
            int dur = db.sql("SELECT duration_min FROM services WHERE id=:s").param("s", pr[1]).query(Integer.class).single();
            for (int k = 0; k < 4; k++) {
                LocalDate d = today.minusDays(5L + k * 9 + (idx % 4));
                if (d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.minusDays(1);
                Instant start = d.atTime(11 + (k % 3), 0).atZone(ALMATY).toInstant();
                long cust = reviewers[(idx + k) % reviewers.length];
                long bk = insertBooking(cust, pr, start, dur, "COMPLETED");
                int rating = ratings[(idx * 3 + k) % ratings.length];
                db.sql("INSERT INTO reviews(booking_id, customer_id, business_id, professional_id, rating, body) VALUES (:b,:c,:bs,:p,:r,:t)")
                  .param("b", bk).param("c", cust).param("bs", pr[2]).param("p", pr[0]).param("r", rating).param("t", reviewTexts[(idx + k) % reviewTexts.length]).update();
            }
            idx++;
        }
        // Anna: a completed visit without a review (so "leave a review" works) and an upcoming confirmed visit
        long[] p0 = allPros.get(0);
        int dur0 = db.sql("SELECT duration_min FROM services WHERE id=:s").param("s", p0[1]).query(Integer.class).single();
        LocalDate past = today.minusDays(10); if (past.getDayOfWeek() == DayOfWeek.SUNDAY) past = past.minusDays(1);
        insertBooking(anna, p0, past.atTime(15, 0).atZone(ALMATY).toInstant(), dur0, "COMPLETED");
        LocalDate up = today.plusDays(3); if (up.getDayOfWeek() == DayOfWeek.SUNDAY) up = up.plusDays(1);
        long upId = insertBooking(anna, p0, up.atTime(12, 0).atZone(ALMATY).toInstant(), dur0, "CONFIRMED");
        db.sql("INSERT INTO booking_status_history(booking_id, from_status, to_status, actor_id, note) VALUES (:b,NULL,'CONFIRMED',:a,'demo seed')").param("b", upId).param("a", anna).update();
        for (long pro : allPros.stream().mapToLong(a -> a[2]).distinct().toArray()) {
            long pid = allPros.stream().filter(a -> a[2] == pro).findFirst().get()[0];
            db.sql("UPDATE businesses SET rating_avg=COALESCE((SELECT round(avg(rating)::numeric,2) FROM reviews WHERE business_id=:b),0), rating_count=(SELECT count(*) FROM reviews WHERE business_id=:b) WHERE id=:b").param("b", pro).update();
        }
        for (long[] pr : allPros)
            db.sql("UPDATE professionals SET rating_avg=COALESCE((SELECT round(avg(rating)::numeric,2) FROM reviews WHERE professional_id=:p),0), rating_count=(SELECT count(*) FROM reviews WHERE professional_id=:p) WHERE id=:p").param("p", pr[0]).update();
        db.sql("INSERT INTO promotions(code, percent_off, valid_to, max_redemptions) VALUES ('WELCOME10', 10, :v, 1000) ON CONFLICT DO NOTHING").param("v", Timestamp.from(clock.instant().plus(Duration.ofDays(365)))).update();
        log.warn("DEMO DATA seeded (fictional businesses). Demo logins use password '{}'. Never enable SULU_SEED_DEMO in production.", PASSWORD);
    }

    private long insertBooking(long customer, long[] pr, Instant start, int durMin, String status) {
        var s = db.sql("SELECT name, price_minor FROM services WHERE id=:s").param("s", pr[1]).query().listOfRows().get(0);
        return db.sql("""
            INSERT INTO bookings(reference, customer_id, business_id, professional_id, service_id, service_name, start_at, end_at, status, price_minor, currency, commission_bps)
            VALUES (:ref,:c,:b,:p,:s,:sn,:st,:en,:status,:price,'KZT',1000) RETURNING id""")
            .param("ref", "SL-D" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase()).param("c", customer).param("b", pr[2]).param("p", pr[0]).param("s", pr[1])
            .param("sn", s.get("name")).param("st", Timestamp.from(start)).param("en", Timestamp.from(start.plus(Duration.ofMinutes(durMin)))).param("status", status).param("price", s.get("price_minor")).query(Long.class).single();
    }

    private long user(String email, String name, String role, String hash) {
        return db.sql("INSERT INTO users(email, password_hash, full_name, role, language, city, email_verified) VALUES (:e,:p,:n,:r,'ru','Алматы',TRUE) RETURNING id")
                .param("e", email).param("p", hash).param("n", name).param("r", role).query(Long.class).single();
    }
}
