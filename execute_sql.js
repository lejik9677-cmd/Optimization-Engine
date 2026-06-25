const postgres = require('postgres');

const DB_CONFIG = {
  host: 'db.kubowqqqawkgghxcktoe.supabase.co',
  port: 5432,
  database: 'postgres',
  username: 'postgres',
  password: 'lejik9677-cmd',
  ssl: 'require'
};

async function execute() {
  const sql = postgres(DB_CONFIG);
  try {
    console.log('🔄 Connecting to Database...');
    const result = await sql`
      SELECT column_name, data_type 
      FROM information_schema.columns 
      WHERE table_schema = 'storage' AND table_name = 'objects'
    `;
    console.log('Columns of storage.objects:', result);

    const sample = await sql`
      SELECT id, name, bucket_id, metadata 
      FROM storage.objects 
      WHERE bucket_id = 'monitoring_data'
      LIMIT 3
    `;
    console.log('Sample objects in monitoring_data:', sample);

  } catch (err) {
    console.error('❌ Error executing SQL:', err);
  } finally {
    await sql.end();
  }
}

execute();

