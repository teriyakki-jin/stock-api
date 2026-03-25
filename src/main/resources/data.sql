INSERT INTO stocks (ticker, name, market, sector, base_price, total_shares, created_at, updated_at)
VALUES
    ('005930', '삼성전자', 'KOSPI', '반도체',  72000,  5969782550, NOW(), NOW()),
    ('000660', 'SK하이닉스', 'KOSPI', '반도체', 178000,  728002365, NOW(), NOW()),
    ('035720', '카카오',    'KOSDAQ', 'IT',     45000,  443273837, NOW(), NOW()),
    ('005380', '현대차',    'KOSPI', '자동차', 230000,  211531506, NOW(), NOW()),
    ('051910', 'LG화학',    'KOSPI', '화학',   320000,   70592343, NOW(), NOW()),
    ('035420', 'NAVER',     'KOSPI', 'IT',     185000,  164263395, NOW(), NOW()),
    ('006400', '삼성SDI',   'KOSPI', '2차전지', 280000,   68764530, NOW(), NOW()),
    ('207940', '삼성바이오로직스', 'KOSPI', '바이오', 780000,  71174000, NOW(), NOW()),
    ('068270', '셀트리온',  'KOSPI', '바이오', 155000,  140064100, NOW(), NOW()),
    ('105560', 'KB금융',    'KOSPI', '금융',    68000,  415819048, NOW(), NOW())
ON CONFLICT (ticker) DO NOTHING;
