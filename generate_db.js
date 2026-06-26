const fs = require('fs');
const initSqlJs = require('sql.js');

(async () => {
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  
  // Criar tabela exatamente como o Room exige
  db.run("PRAGMA user_version = 2;");
  db.run("CREATE TABLE IF NOT EXISTS `taco` (`id` INTEGER NOT NULL, `description` TEXT NOT NULL, `kcal` REAL, `protein` REAL, `carbohydrate` REAL, `lipid` REAL, PRIMARY KEY(`id`))");
  db.run("CREATE TABLE IF NOT EXISTS `daily_consumption` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mealId` INTEGER, `date` TEXT NOT NULL, `kcal` REAL NOT NULL, `protein` REAL NOT NULL, `carbohydrate` REAL NOT NULL, `lipid` REAL NOT NULL, `timestamp` INTEGER NOT NULL)");
  
  // Criar room_master_table para evitar que o Room recrie o banco e limpe os dados (O hash não importa tanto se a tabela bater, mas ter a tabela master é seguro)
  db.run("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
  db.run("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e35b32d9c9497b960def12eb8d8125d3')");

  const alimentos = [
    [1, "Arroz integral, cozido", 124.0, 2.6, 25.8, 1.0],
    [2, "Arroz branco, cozido", 128.0, 2.5, 28.1, 0.2],
    [3, "Feijão preto, cozido", 77.0, 4.5, 14.0, 0.5],
    [4, "Feijão carioca, cozido", 76.0, 4.8, 13.6, 0.5],
    [5, "Carne moída (patinho cozido)", 219.0, 35.9, 0.0, 7.3],
    [6, "Alface crespa, crua", 11.0, 1.3, 1.7, 0.2],
    [7, "Tomate com semente, cru", 15.0, 1.1, 3.1, 0.2],
    [8, "Bacon, frito", 537.0, 23.0, 0.0, 49.7],
    [9, "Linguiça calabresa, frita", 390.0, 22.7, 1.2, 32.4],
    [10, "Carne bovina, acém, cozido", 212.0, 26.7, 0.0, 10.9],
    [11, "Carne bovina, contra-filé, grelhado", 278.0, 29.7, 0.0, 16.6],
    [12, "Frango, peito, grelhado", 159.0, 32.0, 0.0, 2.5],
    [13, "Ovo de galinha, frito", 240.0, 15.6, 1.2, 18.6],
    [14, "Batata doce, cozida", 77.0, 0.6, 18.4, 0.1],
    [15, "Mandioca, cozida", 125.0, 0.6, 30.0, 0.3],
    [16, "Cebola, crua", 39.0, 1.7, 8.9, 0.1],
    [17, "Azeite de oliva extra virgem", 884.0, 0.0, 0.0, 100.0]
  ];
  
  alimentos.forEach(a => {
    db.run("INSERT INTO taco (id, description, kcal, protein, carbohydrate, lipid) VALUES (?, ?, ?, ?, ?, ?)", a);
  });
  
  const data = db.export();
  const buffer = Buffer.from(data);
  fs.mkdirSync('app/src/main/assets/database', { recursive: true });
  fs.writeFileSync('app/src/main/assets/database/taco.db', buffer);
  console.log("Database created successfully!");
})();
