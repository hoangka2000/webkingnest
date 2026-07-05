-- Cập nhật slug bài viết: bỏ "an-thinh-nhan", đổi sang "khanh-hoa"
-- Chạy trên PostgreSQL production sau khi deploy code mới (có redirect 301).

UPDATE news_articles SET slug = 'tam-nhin-thuong-hieu-yen-sao-khanh-hoa'
WHERE slug = 'tam-nhin-thuong-hieu-yen-sao-an-thinh-nhan';

UPDATE news_articles SET slug = 'y-nghia-thuong-hieu-kingnest-khanh-hoa'
WHERE slug = 'y-nghia-thuong-hieu-kingnest-an-thinh-nhan';

UPDATE news_articles SET slug = 'su-menh-cua-yen-sao-khanh-hoa'
WHERE slug = 'su-menh-cua-yen-sao-an-thinh-nhan';

UPDATE news_articles SET slug = 'vi-sao-nen-lua-chon-yen-sao-kingnest-khanh-hoa'
WHERE slug = 'vi-sao-nen-lua-chon-yen-sao-kingnest-an-thinh-nhan';

UPDATE news_articles SET slug = 'thong-diep-thuong-hieu-yen-sao-khanh-hoa'
WHERE slug = 'thong-diep-thuong-hieu-yen-sao-an-thinh-nhan';
