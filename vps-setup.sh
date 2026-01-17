#! /bin/bash
# p2p share vps setup
# set up p2p share on a fresh Ubuntu/Debian VPS

# exit on error
set -e

echo "==== P2P Share VPS Setup ===="
echo "this script will install java, node.js, Nginx, and set up PeerLink"

# update system
echo "Updating system..."
sudo apt-get update
sudo apt-get upgrade -y

# install java
echo "Installing Java..."
sudo apt-get install -y openjdk-17-jdk

# install node.js
echo "Installing Node.js..."
# node.js 20 LTS 설치할 수 있도록 설치 경로랑 목록 정리 -> 다운받은 설치 스크립트를 관리자 권한으로 실행하도록 함
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
# nodejs 설치 (-y : 다 물어보지 말고 그냥 설치)
sudo apt install -y nodejs

# install Nginx
echo "Installing Nginx..."
sudo apt install -y nginx

# install PM2
echo "Installing PM2..."
sudo npm install -g pm2

# install Maven
echo "Installing Maven..."
sudo apt install -y maven

# clone or update git repository
echo "Cloning/updating git repository..."
if [ -d "p2p-share" ]; then
    echo "Directory p2p-share already exists. Removing it to get latest version..."
    rm -rf p2p-share
fi
git clone https://github.com/JangAyeon/p2p-share.git
cd p2p-share
git checkout main
git pull origin main

# build backend
echo "Building backend..."
mvn clean package

# build frontend
echo "Building frontend..."
cd ui
npm install
npm run build
cd ..

# setup nginx
echo "Setting up Nginx..."
# ensure the default site is removed to avoid conflicts
if [ -e /etc/nginx/sites-enabled/default ]; then
    # -e : 파일이나 링크가 존재하면 true
    # default site가 있는 경우에만 처리 진행
    sudo rm /etc/nginx/sites-enabled/default
    # Ubuntu 기본 Nginx "Welcome 페이지" 제거해 우리 서비스가 대신 응답하게 함
    echo "Removed default Nginx site configuration"
fi

# create the p2p share configuration file with the correct content
# 새로운 사이트 설정 파일 만들기
echo "creating /etc/nginx/sites-available/p2p-share ..."
# p2p-share 사이트 설정 파일 만들기 (sites-available : 설정 보관소)
# cat <<EOF : end of file이 나올 때까지 모든 내용을 cat에게 그대로 전달
# tee : 파일에 쓰고 동시에 표준 출력에도 출력
# 서버에 p2p-share Nginx 설정을 자동으로 깔아주는 스크립트
cat <<EOF | sudo tee /etc/nginx/sites-available/p2p-share

server {
    listen 80; # 브라우저 기본 포트 (브라우저에서 주소만 치면 여기로 옴)
    server_name _; # Catch-all for HTTP requests (모든 도메인 요청을 처리하는 패턴)

    # Backend API (/api/ 경로로 오는 요청을 백엔드로 전달)
    # 브라우저가 http://3.34.227.191/api/user로 요청 시 Nginx가 내부에서 http://localhost:8080/user로 전달
    location /api/ {
        proxy_pass http://localhost:8080/; 
        proxy_http_version 1.1;
        # $ 앞에 \ 붙이는 이유: $로 시작하는 경우 쉘 변수로 착각하기 때문에 이스케이프 필요
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host; # 요청한 호스트 이름 유지 (원래 요청한 주소 유지)
        proxy_set_header X-Real-IP \$remote_addr; # 원래 클라이언트 IP 유지
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for; # 프록시 서버 추가 정보 유지
        proxy_set_header X-Forwarded-Proto \$scheme; # 이 요청이 원래 http 또는 https 프로토콜로 왔다는 것을 알려줌
        proxy_cache_bypass \$http_upgrade; 
    }

    # Frontend (/api/ 경로 제외한 모든 요청을 프론트엔드로 전달)
    location / {
        proxy_pass http://localhost:3000; # 해당 요청을 3000번 포트 서버로 전달
        proxy_http_version 1.1; 
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host; # 요청한 호스트 요청한 주소 유지
        proxy_set_header X-Real-IP \$remote_addr; # 원래 클라이언트 IP 유지
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for; # 프록시 서버 추가 정보 유지
        proxy_set_header X-Forwarded-Proto \$scheme; # 요청 프로토콜 유지
        proxy_cache_bypass \$http_upgrade;
    }

    # Additional security headers (still good to have)
    add_header X-Content-Type-Options nosniff;
    add_header X-Frame-Options SAMEORIGIN;
    add_header X-XSS-Protection "1; mode=block";
}
EOF

# create the symbolic link to enable the p2p share site
# sites-available → 설정 보관함
# sites-enabled → 실제로 켜진 설정
# -sf : 심볼릭 링크 생성 (원본 파일이 삭제되어도 링크는 남아있음), -f : 이미 있는 링크 덮어쓰기
sudo ln -sf /etc/nginx/sites-available/p2p-share /etc/nginx/sites-enabled/p2p-share
# Nginx 설정 파일 구문 오류 확인
sudo nginx -t

# $? : 가장 최근에 실행된 명령어의 종료 상태 (0 : 성공, 1 : 실패)
# 여기서는 sudo nginx -t 명령어의 종료 상태를 확인
if [ $? -eq 0 ]; then
    # ensure all dependencies are in classpath
    sudo systemctl restart nginx
    echo "Nginx configured and restarted successfully."
else
    echo "Nginx configuration test failed. Please check /etc/nginx/nginx.conf and /etc/nginx/sites-available/peerlink."
    exit 1
fi

# start backend with PM2
echo "Starting backend with PM2..."
# ensure all dependencies are in classpath
CLASSPATH="target/p2p-1.0-SNAPSHOT.jar:$(mvn dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout -q)"
pm2 start --name p2p-share-backend java -- -cp "$CLASSPATH" p2p.App

# start frontend with PM2
echo "Starting frontend with PM2..."
cd ui
pm2 start npm --name p2p-share-frontend -- start
cd ..

# save PM2 configuration
pm2 save

# set up PM2 to start on boot
echo "Setting up PM2 to start on boot..."
pm2 startup

# follow instructions to printed by the above command
echo "=== Setup Complete ==="
echo "P2P Share is now running on your VPS!"
echo "Backend API: http://localhost:8080 (Internal - accessed via Nginx)"
echo "Frontend: http://3.34.227.191 (Access via your instance's IP address)"
echo "You can access your application using your Lightsail instance's public IP address in your browser."



