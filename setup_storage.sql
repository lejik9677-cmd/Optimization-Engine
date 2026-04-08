-- 1. Create the storage bucket
INSERT INTO storage.buckets (id, name, public)
VALUES ('monitoring_data', 'monitoring_data', true)
ON CONFLICT (id) DO NOTHING;

-- 2. Drop existing policies to avoid conflicts
DROP POLICY IF EXISTS "Public Access" ON storage.objects;
DROP POLICY IF EXISTS "Allow Upload" ON storage.objects;
DROP POLICY IF EXISTS "Allow All" ON storage.objects;

-- 3. Create a policy that allows anyone to upload to this bucket
CREATE POLICY "Allow All" ON storage.objects
FOR ALL
TO public
USING (bucket_id = 'monitoring_data')
WITH CHECK (bucket_id = 'monitoring_data');
