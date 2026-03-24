INSERT INTO stocks (ticker, name, market, base_price, total_shares, created_at, updated_at)
VALUES
    ('005930', '삼성전자', 'KOSPI', 72000, 5969782550, NOW(), NOW()),
    ('000660', 'SK하이닉스', 'KOSPI', 178000, 728002365, NOW(), NOW()),
    ('035720', '카카오', 'KOSDAQ', 45000, 443273837, NOW(), NOW()),
    ('005380', '현대차', 'KOSPI', 230000, 211531506, NOW(), NOW()),
    ('051910', 'LG화학', 'KOSPI', 320000, 70592343, NOW(), NOW())
ON CONFLICT (ticker) DO NOTHING;
