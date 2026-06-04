locals {
  name_prefix = "${var.project_name}-${var.environment}"

  www_domain_name       = "www.${var.domain_name}"
  shortlinkops_base_url = "https://${local.www_domain_name}"
  app_upstream_port     = 8081

  common_tags = merge(
    {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    },
    var.tags
  )
}

resource "aws_route53_zone" "app" {
  name = var.domain_name

  tags = merge(
    local.common_tags,
    {
      Name = var.domain_name
    }
  )
}

data "aws_ssm_parameter" "ubuntu_ami" {
  name = "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id"
}

data "aws_vpc" "default" {
  default = true
}

resource "tls_private_key" "ssh" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "app" {
  key_name   = var.key_pair_name
  public_key = tls_private_key.ssh.public_key_openssh

  tags = merge(
    local.common_tags,
    {
      Name = var.key_pair_name
    }
  )
}

resource "local_sensitive_file" "private_key" {
  content         = tls_private_key.ssh.private_key_pem
  filename        = "${path.module}/${var.private_key_file}"
  file_permission = "0400"
}

resource "aws_security_group" "app" {
  name   = "${local.name_prefix}-sg"
  vpc_id = data.aws_vpc.default.id

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    local.common_tags,
    {
      Name = "${local.name_prefix}-sg"
    }
  )
}

resource "aws_eip" "app" {
  domain = "vpc"

  tags = merge(
    local.common_tags,
    {
      Name = "${local.name_prefix}-eip"
    }
  )
}

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.ubuntu_ami.value
  instance_type          = var.instance_type
  key_name               = aws_key_pair.app.key_name
  vpc_security_group_ids = [aws_security_group.app.id]
  user_data = templatefile("${path.module}/user_data.sh", {
    domain_name           = var.domain_name
    www_domain_name       = local.www_domain_name
    shortlinkops_base_url = local.shortlinkops_base_url
    app_upstream_port     = local.app_upstream_port
    expected_public_ip    = aws_eip.app.public_ip
    certbot_email         = var.certbot_email
  })

  root_block_device {
    volume_size = var.root_volume_size
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(
    local.common_tags,
    {
      Name = "${local.name_prefix}-ec2"
    }
  )
}

resource "aws_eip_association" "app" {
  allocation_id = aws_eip.app.id
  instance_id   = aws_instance.app.id
}

resource "aws_route53_record" "root" {
  zone_id = aws_route53_zone.app.zone_id
  name    = var.domain_name
  type    = "A"
  ttl     = 300
  records = [aws_eip.app.public_ip]
}

resource "aws_route53_record" "www" {
  zone_id = aws_route53_zone.app.zone_id
  name    = local.www_domain_name
  type    = "A"
  ttl     = 300
  records = [aws_eip.app.public_ip]
}
