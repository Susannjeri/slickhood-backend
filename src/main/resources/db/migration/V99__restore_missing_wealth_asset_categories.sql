INSERT IGNORE INTO pms_wealth_asset_type
    (uuid,created_on,active,code,label,description,display_order,market_pricing_allowed)
VALUES
    (UUID_TO_BIN(UUID()),NOW(6),b'1','PROPERTY','Property','Residential or commercial property',10,b'0'),
    (UUID_TO_BIN(UUID()),NOW(6),b'1','LAND','Land','Vacant, agricultural or development land',20,b'0'),
    (UUID_TO_BIN(UUID()),NOW(6),b'1','LISTED_SECURITY','Shares','Exchange-listed shares and securities',30,b'1'),
    (UUID_TO_BIN(UUID()),NOW(6),b'1','FUND','Funds','Unit trusts, mutual funds and exchange-traded funds',40,b'1'),
    (UUID_TO_BIN(UUID()),NOW(6),b'1','GOVERNMENT_SECURITY','Government securities','Treasury bills and bonds',50,b'1'),
    (UUID_TO_BIN(UUID()),NOW(6),b'1','INVESTMENT','Other investments','Private or managed investments',60,b'0'),
    (UUID_TO_BIN(UUID()),NOW(6),b'1','SACCO','SACCO','Deposits and shares held in a SACCO',70,b'0'),
    (UUID_TO_BIN(UUID()),NOW(6),b'1','PENSION','Pension','Retirement and pension accounts',80,b'0'),
    (UUID_TO_BIN(UUID()),NOW(6),b'1','CASH','Cash and bank balances',90,b'0'),
    (UUID_TO_BIN(UUID()),NOW(6),b'1','BUSINESS','Business','Ownership interests in a business',100,b'0'),
    (UUID_TO_BIN(UUID()),NOW(6),b'1','VEHICLE','Vehicle','Cars and other vehicles',110,b'0'),
    (UUID_TO_BIN(UUID()),NOW(6),b'1','DIGITAL_ASSET','Digital assets','Digital assets tracked manually or by market price',120,b'1'),
    (UUID_TO_BIN(UUID()),NOW(6),b'1','OTHER','Other','Other asset types',999,b'0')
ON DUPLICATE KEY UPDATE active=b'1';
