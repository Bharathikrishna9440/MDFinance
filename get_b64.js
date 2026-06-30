const fs = require('fs');
if (fs.existsSync('my-upload-key.jks')) {
    const b64 = fs.readFileSync('my-upload-key.jks').toString('base64');
    fs.writeFileSync('b64.txt', b64);
}
