const postgres = require('postgres');
const sql = postgres('postgres://postgres:lejik9677-cmd@db.kubowqqqawkgghxcktoe.supabase.co:5432/postgres?sslmode=require');

async function setup() {
    try {
        console.log("Ensuring storage bucket exists...");
        
        // 1. Create the bucket if it doesn't exist
        await sql`
            INSERT INTO storage.buckets (id, name, public)
            VALUES ('monitoring_data', 'monitoring_data', true)
            ON CONFLICT (id) DO NOTHING
        `;
        
        console.log("Bucket created or already exists.");

        // 2. Clear existing policies to avoid conflicts during setup
        await sql`DROP POLICY IF EXISTS "Public Access" ON storage.objects`;
        await sql`DROP POLICY IF EXISTS "Allow Upload" ON storage.objects`;
        await sql`DROP POLICY IF EXISTS "Allow All" ON storage.objects`;

        // 3. Create a wide-open policy for testing (we can harden later)
        // This allows Anyone (anon) to upload and read
        await sql`
            CREATE POLICY "Allow All" ON storage.objects
            FOR ALL
            USING (bucket_id = 'monitoring_data')
            WITH CHECK (bucket_id = 'monitoring_data')
        `;

        console.log("RLS Policies updated.");
        
        const buckets = await sql`SELECT * FROM storage.buckets`;
        console.log("Current Buckets:", buckets);

    } catch (e) {
        console.error("Setup Error:", e);
    } finally {
        process.exit();
    }
}

setup();
