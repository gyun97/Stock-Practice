import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const logosDir = path.resolve(__dirname, '../public/logos');

if (!fs.existsSync(logosDir)) {
    console.log('Logos directory not found, skipping normalization.');
    process.exit(0);
}

try {
    const files = fs.readdirSync(logosDir);
    console.log(`Found ${files.length} file(s) in logos directory.`);
    let count = 0;

    files.forEach((file) => {
        const normalized = file.normalize('NFC');
        if (file !== normalized) {
            fs.renameSync(
                path.join(logosDir, file),
                path.join(logosDir, normalized)
            );
            console.log(`Normalized: ${file} -> ${normalized}`);
            count++;
        }
    });

    if (count > 0) {
        console.log(`Successfully normalized ${count} logo(s).`);
    } else {
        console.log('No files needed normalization.');
    }
} catch (error) {
    console.error('Error during normalization:', error);
    // process.exit(0); // Optionally exit gracefully if this script is not critical
}
