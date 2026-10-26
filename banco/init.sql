CREATE TABLE filme (
    id      BIGSERIAL    PRIMARY KEY,
    titulo  VARCHAR(120) NOT NULL,
    diretor VARCHAR(80)  NOT NULL,
    ano     INTEGER      NOT NULL,
    nota    NUMERIC(3,1) NOT NULL
);

INSERT INTO filme (titulo, diretor, ano, nota) VALUES
    ('Cidade de Deus',        'Fernando Meirelles', 2002, 8.6),
    ('Central do Brasil',     'Walter Salles',      1998, 8.0),
    ('O Auto da Compadecida', 'Guel Arraes',        2000, 8.6),
    ('Ainda Estou Aqui',      'Walter Salles',      2024, 8.2),
    ('Carandiru',             'Hector Babenco',     2003, 7.6);
